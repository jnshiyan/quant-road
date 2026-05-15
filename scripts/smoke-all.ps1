param(
    [switch]$Headed
)

$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'run-ui-smoke.ps1'

powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath -Suite all @(
    if ($Headed) { '-Headed' }
)

if ($LASTEXITCODE -ne 0) {
    throw "smoke-all failed with exit code $LASTEXITCODE"
}
