param(
    [string]$AdminBaseUrl = 'http://127.0.0.1:8080',
    [string]$UiBaseUrl = 'http://127.0.0.1:8081',
    [string]$JarPath = 'ruoyi-admin/target/ruoyi-admin.jar',
    [string]$UiDir = 'ruoyi-ui',
    [string]$PythonDir = 'python',
    [long]$DispatchDetailJobId = 0
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
. (Join-Path $repoRoot 'scripts\lib\service-runtime.ps1')

$admin = powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts\check-ruoyi-admin-runtime.ps1') -BaseUrl $AdminBaseUrl -JarPath $JarPath -DispatchDetailJobId $DispatchDetailJobId | ConvertFrom-Json
$uiProcesses = @(Get-RuoyiUiServeProcesses -UiDir $UiDir -RepoRoot $repoRoot)
$workerProcesses = @(Get-QuantWorkerProcesses -PythonDir $PythonDir -RepoRoot $repoRoot)
$workerSupervisorProcesses = @($workerProcesses | Where-Object { $_.CommandLine -like '*run-quant-worker-loop.ps1*' })
$workerRunnerProcesses = @($workerProcesses | Where-Object { $_.CommandLine -like '*run-async-worker*' })

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    admin = $admin
    ui = [ordered]@{
        processCount = $uiProcesses.Count
        healthy = Test-HttpEndpoint -Url $UiBaseUrl -Accept 'text/html'
        processes = @($uiProcesses | ForEach-Object {
            [ordered]@{
                processId = $_.ProcessId
                commandLine = $_.CommandLine
            }
        })
    }
    worker = [ordered]@{
        processCount = $workerProcesses.Count
        supervisorCount = $workerSupervisorProcesses.Count
        runnerCount = $workerRunnerProcesses.Count
        healthy = ($workerSupervisorProcesses.Count -gt 0)
        processes = @($workerProcesses | ForEach-Object {
            [ordered]@{
                processId = $_.ProcessId
                commandLine = $_.CommandLine
            }
        })
    }
}

$result | ConvertTo-Json -Depth 8
