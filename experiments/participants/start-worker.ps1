# Dot-source from the project root so the backend started next inherits the token.
if ($MyInvocation.InvocationName -ne '.') {
    throw 'Uruchom przez dot-source: . .\experiments\participants\start-worker.ps1'
}
$participantWorkerPort = if ($env:MEETER_RECOMMENDER_PORT) { [int]$env:MEETER_RECOMMENDER_PORT } else { 8092 }
if (Get-NetTCPConnection -State Listen -LocalPort $participantWorkerPort -ErrorAction SilentlyContinue) {
    throw "Port $participantWorkerPort jest zajęty. Sprawdź istniejącą usługę modeli przed uruchomieniem kolejnej."
}
$participantPython = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if (!(Test-Path -LiteralPath $participantPython)) {
    throw 'Brak .venv. Utwórz środowisko Pythona zgodnie z README.md.'
}
if (!$env:MEETER_RECOMMENDER_TOKEN) {
    $env:MEETER_RECOMMENDER_TOKEN = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
}
if ($env:MEETER_RECOMMENDER_TOKEN.Length -lt 32 -or $env:MEETER_RECOMMENDER_TOKEN -match '[^\x00-\x7F]') {
    throw 'MEETER_RECOMMENDER_TOKEN musi mieć minimum 32 znaki ASCII.'
}
$participantArtifacts = Join-Path $PSScriptRoot 'artifacts'
New-Item -ItemType Directory -Path $participantArtifacts -Force | Out-Null
$participantWorker = Start-Process -FilePath $participantPython -ArgumentList @(
    ('"' + (Join-Path $PSScriptRoot 'server.py') + '"'), '--port', $participantWorkerPort
) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $participantArtifacts 'worker.out.log') `
    -RedirectStandardError (Join-Path $participantArtifacts 'worker.err.log')
Write-Host "Uruchomiono usługę modeli (PID $($participantWorker.Id)), port $participantWorkerPort. Uruchom backend w tej samej sesji PowerShell."
