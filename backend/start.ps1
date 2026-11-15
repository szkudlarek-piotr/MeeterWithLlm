param([switch]$Rebuild)

$ErrorActionPreference = 'Stop'

Push-Location $PSScriptRoot

try {
    $jar = 'target/meeter-0.0.1-SNAPSHOT.jar'
    $stamp = 'target/start-build.sha256'
    # Include paths as well as contents to detect added, renamed and deleted files.
    $inputs = @(Get-Item pom.xml, mvnw.cmd)
    $inputs += @(Get-ChildItem src, .mvn -File -Recurse -Force)
    $fingerprint = ($inputs | Sort-Object FullName | ForEach-Object {
        $_.FullName.Substring($PSScriptRoot.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }) -join "`n"
    $cached = if (Test-Path -LiteralPath $stamp) { Get-Content -LiteralPath $stamp -Raw } else { '' }
    $current = if (Test-Path -LiteralPath $jar) {
        $fingerprint + "`nJAR:" + (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    } else { '' }

    if ($Rebuild -or !$current -or $cached -cne $current) {
        Write-Host "Building Meeter..."
        # A clean build also removes classes/resources deleted since the last build.
        & .\mvnw.cmd clean package '-DskipTests'

        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed."
        }
        $fingerprint + "`nJAR:" + (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash |
            Set-Content -LiteralPath $stamp -NoNewline
    } else {
        Write-Host "No build changes. Using existing JAR."
    }

    Write-Host "Starting Meeter..."

    & java `
        '-Djavax.net.ssl.trustStoreType=Windows-ROOT' `
        '-Djavax.net.ssl.trustStore=NONE' `
        -jar $jar

    if ($LASTEXITCODE -ne 0) {
        throw "Application failed."
    }
}
finally {
    Pop-Location
}
