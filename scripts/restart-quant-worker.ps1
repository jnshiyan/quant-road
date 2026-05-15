param(
    [string]$PythonDir = 'python',
    [string]$PythonPath = 'python',
    [string]$WorkerId = 'ruoyi-web-worker',
    [switch]$StopOnly,
    [switch]$StartOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
$runtimeDir = Join-Path $repoRoot 'runtime-logs'
. (Join-Path $repoRoot 'scripts\lib\service-runtime.ps1')

function Stop-QuantWorker {
    $processes = @(Get-QuantWorkerProcesses -PythonDir $PythonDir -RepoRoot $repoRoot)
    if (-not $processes) {
        Write-Host 'No running quant worker process found.' -ForegroundColor DarkGray
        return @()
    }

    $ids = @($processes | Select-Object -ExpandProperty ProcessId)
    Write-Host "Stopping quant worker process(es): $($ids -join ', ')" -ForegroundColor Yellow
    return @(Stop-ProcessIds -ProcessIds $ids)
}

function Start-QuantWorker {
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
    $stdout = Join-Path $runtimeDir 'quant-worker.stdout.log'
    $stderr = Join-Path $runtimeDir 'quant-worker.stderr.log'
    $scriptPath = Join-Path $repoRoot 'scripts\run-quant-worker-loop.ps1'

    Write-Host "Starting quant worker supervisor with worker-id=$WorkerId" -ForegroundColor Cyan
    $proc = Start-LoggedProcess `
        -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath, '-PythonDir', $PythonDir, '-PythonPath', $PythonPath, '-WorkerId', $WorkerId) `
        -WorkingDirectory $repoRoot `
        -StdoutPath $stdout `
        -StderrPath $stderr

    if (-not (Wait-ProcessStable -ProcessId $proc.Id -StableSeconds 3)) {
        throw "Quant worker exited immediately after launch. Check $stdout and $stderr"
    }

    Write-Host "Started pid=$($proc.Id)" -ForegroundColor Green
    return $proc
}

if ($StopOnly -and $StartOnly) {
    throw 'StopOnly and StartOnly cannot be used together.'
}

if (-not $StartOnly) {
    Stop-QuantWorker | Out-Null
}

if ($StopOnly) {
    Write-Host 'quant worker stop-only completed.' -ForegroundColor Green
    return
}

Start-QuantWorker | Out-Null
Write-Host 'quant worker restart completed successfully.' -ForegroundColor Green
