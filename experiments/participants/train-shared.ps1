[CmdletBinding()]
param(
    [ValidateRange(1, 64)][int]$Threads = 2,
    [ValidateSet('start_date', 'created_at')][string]$SplitKey = 'start_date',
    [string]$Revision = 'main',
    [ValidateRange(0.01, 365000)][double]$HalfLifeDays = 365,
    [switch]$SkipDependencies
)
$ErrorActionPreference = 'Stop'
$participantProject = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$participantPython = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
$participantConfig = Join-Path $participantProject 'set_agent_sandbo_env.ps1'
$participantOutput = Join-Path $PSScriptRoot 'artifacts'

function Assert-ParticipantExit([string]$Stage) {
    if ($LASTEXITCODE -ne 0) { throw "$Stage - proces zakonczyl sie kodem $LASTEXITCODE." }
}

Push-Location $participantProject
$participantOldOmp = $env:OMP_NUM_THREADS
$participantOldMkl = $env:MKL_NUM_THREADS
try {
    if (!(Test-Path -LiteralPath $participantConfig)) {
        throw 'Brak set_agent_sandbo_env.ps1 w katalogu projektu.'
    }
    if (!(Test-Path -LiteralPath $participantPython)) {
        Write-Host '[1/4] Tworzenie srodowiska .venv...'
        if (Get-Command py.exe -ErrorAction SilentlyContinue) {
            & py.exe -3 -m venv (Join-Path $PSScriptRoot '.venv')
        } else {
            & python.exe -m venv (Join-Path $PSScriptRoot '.venv')
        }
        Assert-ParticipantExit 'Tworzenie .venv'
    } else { Write-Host '[1/4] Korzystam z istniejacego .venv.' }

    if (!$SkipDependencies) {
        Write-Host '[2/4] Sprawdzanie i instalacja zaleznosci...'
        & $participantPython -m pip install -r (Join-Path $PSScriptRoot 'requirements.txt')
        Assert-ParticipantExit 'Instalacja zaleznosci'
    } else { Write-Host '[2/4] Pominieto instalacje zaleznosci.' }

    Write-Host '[3/4] Wczytywanie konfiguracji bazy (bez wypisywania wartosci)...'
    . $participantConfig
    foreach ($participantVariable in @('DB_URL', 'DB_USER', 'DB_PASSWORD')) {
        if (![Environment]::GetEnvironmentVariable($participantVariable)) {
            throw "Brak zmiennej $participantVariable po wczytaniu konfiguracji."
        }
    }
    New-Item -ItemType Directory -Path $participantOutput -Force | Out-Null
    $participantRunLog = Join-Path $participantOutput ('shared-training-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    & $participantPython -c "from importlib.metadata import distributions; print('\n'.join(sorted(d.metadata['Name']+'=='+d.version for d in distributions())))" |
        Set-Content -LiteralPath (Join-Path $participantOutput 'shared-requirements-lock.txt') -Encoding utf8
    Assert-ParticipantExit 'Zapis wersji zaleznosci'
    $env:OMP_NUM_THREADS = [string]$Threads
    $env:MKL_NUM_THREADS = [string]$Threads
    Write-Host '[4/4] Trening wspolnych modeli. Pierwsze pobranie RoBERTy i obliczenia na CPU moga potrwac.'
    # Windows PowerShell 5 treats redirected native stderr (including harmless
    # transformer warnings) as ErrorRecord. Exit code decides whether training failed.
    $participantOldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $participantPython -u (Join-Path $PSScriptRoot 'shared.py') --output $participantOutput --split-key $SplitKey --threads $Threads --revision $Revision --half-life-days $HalfLifeDays.ToString([System.Globalization.CultureInfo]::InvariantCulture) 2>&1 |
            Tee-Object -FilePath $participantRunLog
    } finally { $ErrorActionPreference = $participantOldPreference }
    Assert-ParticipantExit 'Trening'
    Write-Host "Gotowe. Wyniki: $(Join-Path $participantOutput 'shared_metrics.json')"
    Write-Host 'Zrestartuj backend, aby zaladowac nowe modele, a w formularzu kliknij Sprawdz ponownie.'
} finally {
    $env:OMP_NUM_THREADS = $participantOldOmp
    $env:MKL_NUM_THREADS = $participantOldMkl
    Pop-Location
}
