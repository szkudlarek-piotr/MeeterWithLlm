[CmdletBinding()]
param(
    [string]$DestinationRoot = [Environment]::GetFolderPath('MyDocuments'),
    [string]$DumpExecutable,
    [ValidateRange(1, 65535)][int]$BackendPort = $(if ($env:SERVER_PORT) { $env:SERVER_PORT } else { 8091 }),
    [hashtable]$PhotoDirectories = @{}
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot 'backend'
$optionsFile = $null
$stderrFile = $null
$backup = $null

function ConvertTo-OptionValue([string]$Value) {
    if ($Value -match '[\r\n\x00]') { throw 'Nieprawidlowy znak w konfiguracji polaczenia.' }
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}

try {
    # Use exactly the environment inherited from the caller. Never load a sandbox
    # script or silently replace the selected database with a local default.
    if ([string]::IsNullOrWhiteSpace($env:DB_URL) -or [string]::IsNullOrWhiteSpace($env:DB_USER) -or $null -eq $env:DB_PASSWORD) {
        throw 'Ustaw DB_URL, DB_USER i DB_PASSWORD w tej samej sesji PowerShell co backup. Skrypt nie laduje konfiguracji sandboxa.'
    }
    # Refuse rather than silently stop a running application.
    if (Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -eq $BackendPort }) {
        throw "Zatrzymaj backend (port $BackendPort) przed wykonaniem kopii."
    }
    $connection = $null
    if ($env:DB_URL -notmatch '^jdbc:(mariadb|mysql)://' -or
        ![Uri]::TryCreate($env:DB_URL.Substring(5), [UriKind]::Absolute, [ref]$connection)) {
        throw 'Obslugiwany jest pojedynczy adres JDBC MariaDB/MySQL.'
    }
    $database = [Uri]::UnescapeDataString($connection.AbsolutePath.Trim('/'))
    if (!$connection.Host -or $connection.UserInfo -or $database -notmatch '^[A-Za-z0-9_]+$' -or $connection.Fragment) {
        throw 'Nieobslugiwany format adresu bazy. Dane logowania ustaw osobno w DB_USER/DB_PASSWORD.'
    }
    # Never silently discard JDBC options, e.g. TLS requirements.
    if ($connection.Query) { throw 'Adres DB_URL zawiera opcje JDBC. Skonfiguruj adres bez opcji dla klienta dump; wymagania TLS nie moga byc pomijane.' }
    $dbPort = if ($connection.Port -gt 0) { $connection.Port } else { 3306 }

    if (!$DumpExecutable) {
        foreach ($name in @('mariadb-dump.exe', 'mysqldump.exe')) {
            $command = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($command) { $DumpExecutable = $command.Source; break }
        }
        if (!$DumpExecutable) {
            $installRoots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}) | Where-Object { $_ }
            $candidates = foreach ($root in $installRoots) {
                foreach ($pattern in @('MariaDB*\bin\mariadb-dump.exe', 'MySQL\*\bin\mysqldump.exe')) {
                    Get-Item -Path (Join-Path $root $pattern) -ErrorAction SilentlyContinue
                }
            }
            $DumpExecutable = ($candidates | Sort-Object FullName | Select-Object -First 1).FullName
        }
    }
    if (!$DumpExecutable -or !(Test-Path -LiteralPath $DumpExecutable -PathType Leaf)) {
        throw 'Nie znaleziono mariadb-dump.exe/mysqldump.exe. Zainstaluj klienta lub podaj -DumpExecutable z pelna sciezka.'
    }

    # Resolve configured photo paths relative to backend, just like the application.
    $properties = ConvertFrom-StringData ([IO.File]::ReadAllText((Join-Path $backend 'src/main/resources/application.properties')).Replace('\', '\\'))
    $definitions = [ordered]@{
        people = @('app.photos.directory', 'APP_PHOTOS_DIRECTORY', './data/photos/people')
        events = @('app.photos.events-directory', 'APP_PHOTOS_EVENTSDIRECTORY', './data/photos/events')
        former = @('app.photos.former-directory', 'APP_PHOTOS_FORMERDIRECTORY', './data/photos/former-people')
        cliques = @('app.photos.cliques-directory', 'APP_PHOTOS_CLIQUESDIRECTORY', './data/photos/cliques')
        categories = @('app.photos.place-categories-directory', 'APP_PHOTOS_PLACECATEGORIESDIRECTORY', './data/photos/placeCategories')
    }
    foreach ($key in $PhotoDirectories.Keys) {
        if (!$definitions.Contains($key)) { throw "Nieznany katalog zdjec: $key" }
    }
    $sources = [ordered]@{}
    foreach ($key in $definitions.Keys) {
        $definition = $definitions[$key]
        $source = if ($PhotoDirectories.ContainsKey($key)) { $PhotoDirectories[$key] }
            elseif ([Environment]::GetEnvironmentVariable($definition[1])) { [Environment]::GetEnvironmentVariable($definition[1]) }
            elseif ($properties.ContainsKey($definition[0])) { $properties[$definition[0]] }
            else { $definition[2] }
        if (!$source -or $source -match '\$\{') { throw "Podaj jawna sciezke -PhotoDirectories @{ $key = '...' }." }
        if (![IO.Path]::IsPathRooted($source)) { $source = Join-Path $backend $source }
        $sources[$key] = [IO.Path]::GetFullPath($source)
    }

    if (!$DestinationRoot) { throw 'Nie znaleziono folderu Dokumenty. Podaj -DestinationRoot.' }
    $DestinationRoot = [IO.Path]::GetFullPath($DestinationRoot)
    $backup = Join-Path $DestinationRoot ('backup_' + (Get-Date -Format 'dd_MM_yyyy_HH_mm'))
    if (Test-Path -LiteralPath $backup) { throw 'Folder kopii z tej minuty juz istnieje. Nie zostanie nadpisany; uruchom skrypt w kolejnej minucie.' }
    foreach ($source in $sources.Values) {
        if ($backup.StartsWith($source.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $backup -eq $source) {
            throw 'Folder backupu nie moze znajdowac sie wewnatrz kopiowanych zdjec.'
        }
    }
    New-Item -ItemType Directory -Path $backup | Out-Null
    $marker = Join-Path $backup 'INCOMPLETE.txt'
    [IO.File]::WriteAllText($marker, 'Kopia nie zostala ukonczona. Nie uzywaj jej do odtwarzania.')

    # Keep the password out of command-line arguments and the backup itself.
    $optionsFile = Join-Path ([IO.Path]::GetTempPath()) ('meeter-dump-' + [Guid]::NewGuid().ToString('N') + '.cnf')
    New-Item -ItemType File -Path $optionsFile | Out-Null
    $acl = New-Object Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')))
    Set-Acl -LiteralPath $optionsFile -AclObject $acl
    $options = @('[client]', 'protocol=tcp', ('host=' + (ConvertTo-OptionValue $connection.Host)),
        "port=$dbPort", ('user=' + (ConvertTo-OptionValue $env:DB_USER)),
        ('password=' + (ConvertTo-OptionValue $env:DB_PASSWORD))) -join "`r`n"
    [IO.File]::WriteAllText($optionsFile, $options, (New-Object Text.UTF8Encoding($false)))
    $stderrFile = Join-Path ([IO.Path]::GetTempPath()) ('meeter-dump-' + [Guid]::NewGuid().ToString('N') + '.err')
    $dumpFile = Join-Path $backup 'database.sql'
    Write-Host 'Eksport bazy danych...'
    try {
        & $DumpExecutable "--defaults-extra-file=$optionsFile" '--single-transaction' '--quick' '--routines' '--events' '--triggers' '--hex-blob' '--default-character-set=utf8mb4' "--result-file=$dumpFile" $database 2> $stderrFile | Out-Null
        $dumpExit = $LASTEXITCODE
    } catch { throw 'Eksport bazy nie powiodl sie. Sprawdz konfiguracje i uprawnienia klienta dump.' }
    if ($dumpExit -ne 0 -or !(Test-Path -LiteralPath $dumpFile) -or (Get-Item -LiteralPath $dumpFile).Length -eq 0) {
        throw 'Eksport bazy nie powiodl sie lub utworzyl pusty plik. Kopia pozostaje niekompletna.'
    }

    $photoRoot = Join-Path $backup 'photos'
    New-Item -ItemType Directory -Path $photoRoot | Out-Null
    $missing = @()
    foreach ($key in $sources.Keys) {
        $source = $sources[$key]
        if (!(Test-Path -LiteralPath $source -PathType Container)) { $missing += $key; continue }
        $links = @((Get-Item -LiteralPath $source -Force)) + @(Get-ChildItem -LiteralPath $source -Recurse -Force)
        if ($links | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) {
            throw "Katalog $key zawiera dowiazanie. Wskaz rzeczywista sciezke zamiast dowiazania."
        }
        Write-Host "Kopiowanie zdjec: $key..."
        Copy-Item -LiteralPath $source -Destination (Join-Path $photoRoot $key) -Recurse -Force
    }
    # A restarted backend could invalidate consistency between dump and photos.
    if (Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -eq $BackendPort }) {
        throw 'Backend zostal uruchomiony podczas kopiowania. Powtorz backup przy zatrzymanym backendzie.'
    }
    $files = @(Get-ChildItem -LiteralPath $backup -Recurse -File | Where-Object { $_.FullName -ne $marker } | ForEach-Object {
        [ordered]@{ path = $_.FullName.Substring($backup.Length + 1); bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    })
    [ordered]@{ createdAt = (Get-Date).ToString('o'); databaseFormat = 'MariaDB/MySQL SQL'; missingPhotoDirectories = $missing; files = $files } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding UTF8
    @'
Meeter: kopia bazy i zdjec

database.sql: eksport jednej bazy MariaDB/MySQL (tabele, dane, procedury, zdarzenia, wyzwalacze).
photos/people, events, former, cliques, categories: odpowiednie foldery zdjec.
manifest.json: lista plikow z rozmiarami i SHA256; missingPhotoDirectories wymienia foldery,
ktore nie istnialy podczas backupu (np. archiwum przed pierwsza zmiana zdjecia).

Odtwarzanie: zatrzymaj backend. Zaimportuj database.sql klientem MariaDB/MySQL do wybranej
bazy, a foldery photos przywroc do skonfigurowanych katalogow aplikacji. Sprawdz sciezki
i konfiguracje polaczenia przed uruchomieniem. Ten skrypt nie wykonuje odtwarzania.
Plik SQL zawiera dane osobowe i hashe hasel kont; przechowuj caly backup prywatnie.
Zewnetrzne zapisy do bazy musza byc wstrzymane na czas kopii. --single-transaction
zapewnia spojny eksport tabel InnoDB; nie wykonuj zmian DDL podczas eksportu.
'@ | Set-Content -LiteralPath (Join-Path $backup 'README.txt') -Encoding UTF8
    Remove-Item -LiteralPath $marker
    Write-Host "Backup gotowy: $backup"
    if ($missing.Count) { Write-Host ('Nieistniejace foldery zdjec (pominiete): ' + ($missing -join ', ')) }
} finally {
    foreach ($temporary in @($optionsFile, $stderrFile)) {
        if ($temporary -and (Test-Path -LiteralPath $temporary)) { Remove-Item -LiteralPath $temporary -Force }
    }
}
