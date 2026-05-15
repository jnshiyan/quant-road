param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$UiDir = 'ruoyi-ui',
    [string]$NpmPath = 'npm.cmd',
    [switch]$StopOnly,
    [switch]$StartOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
$runtimeDir = Join-Path $repoRoot 'runtime-logs'
. (Join-Path $repoRoot 'scripts\lib\service-runtime.ps1')

function Stop-RuoyiUi {
    $serveProcesses = @(Get-RuoyiUiServeProcesses -UiDir $UiDir -RepoRoot $repoRoot)
    if (-not $serveProcesses) {
        Write-Host 'No running ruoyi-ui dev server process found.' -ForegroundColor DarkGray
        return @()
    }

    $serveIds = @($serveProcesses | Select-Object -ExpandProperty ProcessId)
    $parentIds = @(Get-ParentProcessIds -ChildProcessIds $serveIds -AllowedNames @('node.exe'))
    $ids = @($serveIds + $parentIds | Select-Object -Unique)
    Write-Host "Stopping ruoyi-ui process(es): $($ids -join ', ')" -ForegroundColor Yellow
    return @(Stop-ProcessIds -ProcessIds $ids)
}

function Start-RuoyiUi {
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
    $stdout = Join-Path $runtimeDir 'ruoyi-ui.stdout.log'
    $stderr = Join-Path $runtimeDir 'ruoyi-ui.stderr.log'
    $uiFullPath = Resolve-ServicePath -RepoRoot $repoRoot -RelativePath $UiDir

    Write-Host "Starting ruoyi-ui dev server in $uiFullPath" -ForegroundColor Cyan
    $proc = Start-LoggedProcess `
        -FilePath $NpmPath `
        -ArgumentList @('run', 'dev') `
        -WorkingDirectory $uiFullPath `
        -StdoutPath $stdout `
        -StderrPath $stderr

    Write-Host "Started pid=$($proc.Id)" -ForegroundColor Green
    Wait-HttpEndpoint -Url $BaseUrl -Accept 'text/html'
    return $proc
}

if ($StopOnly -and $StartOnly) {
    throw 'StopOnly and StartOnly cannot be used together.'
}

if (-not $StartOnly) {
    Stop-RuoyiUi | Out-Null
}

if ($StopOnly) {
    Write-Host 'ruoyi-ui stop-only completed.' -ForegroundColor Green
    return
}

Start-RuoyiUi | Out-Null
Write-Host 'ruoyi-ui restart completed successfully.' -ForegroundColor Green
