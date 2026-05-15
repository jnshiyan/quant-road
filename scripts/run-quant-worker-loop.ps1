param(
    [string]$PythonDir = 'python',
    [string]$PythonPath = 'python',
    [string]$WorkerId = 'ruoyi-web-worker',
    [int]$IdleSleepSeconds = 5
)

$ErrorActionPreference = 'Stop'
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pythonFullPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $PythonDir))
$previousPythonPath = $env:PYTHONPATH
$env:PYTHONPATH = 'src'

try {
    Set-Location $pythonFullPath
    while ($true) {
        & $PythonPath -m quant_road run-async-worker --worker-id $WorkerId
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            Write-Host "quant worker exited with code $exitCode, retrying in $IdleSleepSeconds s" -ForegroundColor Yellow
        }
        Start-Sleep -Seconds $IdleSleepSeconds
    }
} finally {
    $env:PYTHONPATH = $previousPythonPath
}
