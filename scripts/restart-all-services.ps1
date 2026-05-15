param(
    [switch]$SkipAdmin,
    [switch]$SkipUi,
    [switch]$SkipWorker,
    [string]$WorkerId = 'ruoyi-web-worker',
    [string]$AdminBaseUrl = 'http://127.0.0.1:8080',
    [string]$UiBaseUrl = 'http://127.0.0.1:8081',
    [string]$JarPath = 'ruoyi-admin/target/ruoyi-admin.jar',
    [string]$UiDir = 'ruoyi-ui',
    [string]$PythonDir = 'python',
    [long]$DispatchDetailJobId = 0
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path

$adminScript = Join-Path $repoRoot 'scripts\restart-ruoyi-admin.ps1'
$uiScript = Join-Path $repoRoot 'scripts\restart-ruoyi-ui.ps1'
$workerScript = Join-Path $repoRoot 'scripts\restart-quant-worker.ps1'
$checkScript = Join-Path $repoRoot 'scripts\check-all-services-runtime.ps1'

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host "[$Name] starting..." -ForegroundColor Cyan
    & $Action
    Write-Host "[$Name] done." -ForegroundColor Green
}

if (-not $SkipWorker) {
    Invoke-Step -Name 'stop-worker' -Action { powershell -NoProfile -ExecutionPolicy Bypass -File $workerScript -StopOnly }
}
if (-not $SkipUi) {
    Invoke-Step -Name 'stop-ui' -Action { powershell -NoProfile -ExecutionPolicy Bypass -File $uiScript -StopOnly }
}
if (-not $SkipAdmin) {
    Invoke-Step -Name 'stop-admin' -Action { powershell -NoProfile -ExecutionPolicy Bypass -File $adminScript -StopOnly }
}

if (-not $SkipAdmin) {
    Invoke-Step -Name 'start-admin' -Action { powershell -NoProfile -ExecutionPolicy Bypass -File $adminScript -StartOnly -SkipTests -SkipPackage }
}
if (-not $SkipUi) {
    Invoke-Step -Name 'start-ui' -Action { powershell -NoProfile -ExecutionPolicy Bypass -File $uiScript -StartOnly }
}
if (-not $SkipWorker) {
    Invoke-Step -Name 'start-worker' -Action { powershell -NoProfile -ExecutionPolicy Bypass -File $workerScript -StartOnly -WorkerId $WorkerId }
}

$checkArgs = @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', $checkScript,
    '-AdminBaseUrl', $AdminBaseUrl,
    '-UiBaseUrl', $UiBaseUrl,
    '-JarPath', $JarPath,
    '-UiDir', $UiDir,
    '-PythonDir', $PythonDir,
    '-DispatchDetailJobId', $DispatchDetailJobId
)

Write-Host '[verify] checking runtime health...' -ForegroundColor Cyan
$runtime = powershell @checkArgs | ConvertFrom-Json

if ((-not $SkipAdmin) -and (-not $runtime.admin.baseHealth.ok -or -not $runtime.admin.login.ok)) {
    throw 'ruoyi-admin health verification failed after restart.'
}
if ((-not $SkipUi) -and (-not $runtime.ui.healthy)) {
    throw 'ruoyi-ui health verification failed after restart.'
}
if ((-not $SkipWorker) -and (-not $runtime.worker.healthy)) {
    throw 'quant worker health verification failed after restart.'
}

Write-Host 'All requested services restarted successfully.' -ForegroundColor Green
$runtime | ConvertTo-Json -Depth 8
