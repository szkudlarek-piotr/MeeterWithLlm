param([ValidateRange(0, 86400)][int]$IntervalSeconds = 0)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Credentials are deliberately not printed or passed on the command line.
    . (Join-Path $PSScriptRoot '..\set_agent_sandbo_env.ps1')
    & .\mvnw.cmd -q compile exec:java "-Dexec.args=$IntervalSeconds"
    if ($LASTEXITCODE -ne 0) { throw 'Graph synchronization failed.' }
} finally {
    Pop-Location
}
