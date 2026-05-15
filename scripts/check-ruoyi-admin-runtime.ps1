param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$JarPath = 'ruoyi-admin/target/ruoyi-admin.jar',
    [long]$DispatchDetailJobId = 0,
    [string]$Username = 'admin',
    [string]$Password = 'admin123'
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
. (Join-Path $repoRoot 'scripts\lib\service-runtime.ps1')

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )

    if ($Body) {
        return Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -ContentType 'application/json' -Body $Body -TimeoutSec 20
    }
    return Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -TimeoutSec 20
}

function Try-InvokeWebRequestPayload {
    param(
        [string]$Url,
        [hashtable]$Headers = @{}
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing -TimeoutSec 20
        return [pscustomobject]@{
            Ok = $true
            StatusCode = [int]$response.StatusCode
            Body = $response.Content
        }
    } catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            return [pscustomobject]@{
                Ok = $false
                StatusCode = [int]$_.Exception.Response.StatusCode.value__
                Body = $body
            }
        }
        return [pscustomobject]@{
            Ok = $false
            StatusCode = -1
            Body = $_.Exception.Message
        }
    }
}

function Format-ProcessCreationDate {
    param($CreationDate)

    if (-not $CreationDate) {
        return $null
    }

    try {
        return ([System.Management.ManagementDateTimeConverter]::ToDateTime($CreationDate)).ToString('yyyy-MM-dd HH:mm:ss')
    } catch {
        return [string]$CreationDate
    }
}

$jarFullPath = Resolve-ServicePath -RepoRoot $repoRoot -RelativePath $JarPath
$jarInfo = if (Test-Path $jarFullPath) { Get-Item $jarFullPath } else { $null }
$processes = @(Get-RuoyiAdminJarProcesses -JarPath $JarPath -RepoRoot $repoRoot)
$baseHealth = Try-InvokeWebRequestPayload -Url $BaseUrl

$token = $null
$loginError = $null
try {
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $loginResp = Invoke-JsonRequest -Method 'POST' -Url "$BaseUrl/login" -Body $loginBody
    if ($loginResp.code -eq 200 -and $loginResp.token) {
        $token = $loginResp.token
    } else {
        $loginError = "login returned code=$($loginResp.code)"
    }
} catch {
    $loginError = $_.Exception.Message
}

$taskCenter = if ($token) {
    Try-InvokeWebRequestPayload -Url "$BaseUrl/quant/data/taskCenterSummary" -Headers @{ Authorization = "Bearer $token" }
} else {
    $null
}

$dispatchDetail = if ($token -and $DispatchDetailJobId -gt 0) {
    Try-InvokeWebRequestPayload -Url "$BaseUrl/quant/data/dispatchDetail/$DispatchDetailJobId" -Headers @{ Authorization = "Bearer $token" }
} else {
    $null
}

$result = [ordered]@{
    checkedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    baseUrl = $BaseUrl
    jarPath = $jarFullPath
    jarExists = [bool]$jarInfo
    jarLastWriteTime = if ($jarInfo) { $jarInfo.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss') } else { $null }
    jarLength = if ($jarInfo) { $jarInfo.Length } else { $null }
    processCount = $processes.Count
    processes = @(
        $processes | ForEach-Object {
            [ordered]@{
                processId = $_.ProcessId
                creationDate = Format-ProcessCreationDate -CreationDate $_.CreationDate
                commandLine = $_.CommandLine
            }
        }
    )
    baseHealth = [ordered]@{
        ok = $baseHealth.Ok
        statusCode = $baseHealth.StatusCode
        bodyPreview = if ($baseHealth.Body) { $baseHealth.Body.Substring(0, [Math]::Min(200, $baseHealth.Body.Length)) } else { '' }
    }
    login = [ordered]@{
        ok = [bool]$token
        error = $loginError
    }
    taskCenterSummary = if ($taskCenter) {
        [ordered]@{
            ok = $taskCenter.Ok
            statusCode = $taskCenter.StatusCode
            bodyPreview = if ($taskCenter.Body) { $taskCenter.Body.Substring(0, [Math]::Min(200, $taskCenter.Body.Length)) } else { '' }
        }
    } else {
        $null
    }
    dispatchDetail = if ($dispatchDetail) {
        [ordered]@{
            jobId = $DispatchDetailJobId
            ok = $dispatchDetail.Ok
            statusCode = $dispatchDetail.StatusCode
            bodyPreview = if ($dispatchDetail.Body) { $dispatchDetail.Body.Substring(0, [Math]::Min(200, $dispatchDetail.Body.Length)) } else { '' }
        }
    } else {
        $null
    }
}

$result | ConvertTo-Json -Depth 6
