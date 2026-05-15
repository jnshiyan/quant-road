param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = 'admin123'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [string]$Body = $null
    )

    try {
        if ($Body) {
            $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $Headers -ContentType 'application/json' -Body $Body
        } else {
            $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $Headers
        }

        $httpCode = [int]$response.StatusCode
        $json = $null
        try { $json = $response.Content | ConvertFrom-Json } catch {}
        $bizCode = if ($null -ne $json -and $null -ne $json.code) { [int]$json.code } else { -999 }
        $pass = ($httpCode -eq 200) -and (($bizCode -eq 200) -or $bizCode -eq -999)

        return [pscustomobject]@{
            name   = $Name
            method = $Method
            url    = $Url
            http   = $httpCode
            code   = $bizCode
            pass   = $pass
            error  = ''
            data   = $json
        }
    } catch {
        return [pscustomobject]@{
            name   = $Name
            method = $Method
            url    = $Url
            http   = -1
            code   = -1
            pass   = $false
            error  = $_.Exception.Message
            data   = $null
        }
    }
}

function Invoke-ApiMultipart {
    param(
        [string]$Name,
        [string]$Url,
        [hashtable]$Headers,
        [string]$FilePath,
        [hashtable]$FormFields
    )

    try {
        if (-not (Test-Path $FilePath)) {
            throw "file not found: $FilePath"
        }
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $Url)
        foreach ($headerKey in $Headers.Keys) {
            [void]$request.Headers.TryAddWithoutValidation($headerKey, [string]$Headers[$headerKey])
        }

        $multipart = [System.Net.Http.MultipartFormDataContent]::new()
        if ($FormFields) {
            foreach ($k in $FormFields.Keys) {
                $multipart.Add([System.Net.Http.StringContent]::new([string]$FormFields[$k]), $k)
            }
        }

        $stream = [System.IO.File]::OpenRead($FilePath)
        try {
            $fileContent = [System.Net.Http.StreamContent]::new($stream)
            $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/csv')
            $multipart.Add($fileContent, 'file', [System.IO.Path]::GetFileName($FilePath))
            $request.Content = $multipart

            $client = [System.Net.Http.HttpClient]::new()
            try {
                $response = $client.SendAsync($request).GetAwaiter().GetResult()
                $httpCode = [int]$response.StatusCode
                $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            } finally {
                $client.Dispose()
            }
        } finally {
            $stream.Dispose()
            $multipart.Dispose()
            $request.Dispose()
        }

        $json = $null
        try { $json = $content | ConvertFrom-Json } catch {}
        $bizCode = if ($null -ne $json -and $null -ne $json.code) { [int]$json.code } else { -999 }
        $pass = ($httpCode -eq 200) -and (($bizCode -eq 200) -or $bizCode -eq -999)
        return [pscustomobject]@{
            name   = $Name
            method = 'POST'
            url    = $Url
            http   = $httpCode
            code   = $bizCode
            pass   = $pass
            error  = ''
        }
    } catch {
        return [pscustomobject]@{
            name   = $Name
            method = 'POST'
            url    = $Url
            http   = -1
            code   = -1
            pass   = $false
            error  = $_.Exception.Message
        }
    }
}

function New-ExecutionImportSmokeCsv {
    param(
        [string]$TemplatePath,
        [string]$OutputPath
    )

    if (-not (Test-Path $TemplatePath)) {
        throw "template file not found: $TemplatePath"
    }

    $runSuffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $rows = Import-Csv -Path $TemplatePath
    for ($i = 0; $i -lt $rows.Count; $i++) {
        $price = [decimal]::Parse([string]$rows[$i].price, [System.Globalization.CultureInfo]::InvariantCulture)
        $delta = [decimal](($runSuffix + $i) % 97) / 100
        $rows[$i].price = ($price + $delta).ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
        $externalId = [string]$rows[$i].external_order_id
        if ([string]::IsNullOrWhiteSpace($externalId)) {
            $rows[$i].external_order_id = "SMOKE-$runSuffix-$i"
        } else {
            $rows[$i].external_order_id = "$externalId-SMOKE-$runSuffix-$i"
        }
    }
    $rows | Export-Csv -Path $OutputPath -NoTypeInformation -Encoding UTF8
}

$loginPayload = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$loginResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$BaseUrl/login" -ContentType 'application/json' -Body $loginPayload
$loginJson = $loginResponse.Content | ConvertFrom-Json
if ($loginJson.code -ne 200 -or -not $loginJson.token) {
    throw "Login failed: $($loginResponse.Content)"
}

$authHeaders = @{ Authorization = "Bearer $($loginJson.token)" }

$cases = @(
    @{ name = 'getInfo'; method = 'GET'; url = "$BaseUrl/getInfo" },
    @{ name = 'getRouters'; method = 'GET'; url = "$BaseUrl/getRouters" },
    @{ name = 'system.user.list'; method = 'GET'; url = "$BaseUrl/system/user/list?pageNum=1&pageSize=10" },
    @{ name = 'system.role.list'; method = 'GET'; url = "$BaseUrl/system/role/list?pageNum=1&pageSize=10" },
    @{ name = 'system.menu.list'; method = 'GET'; url = "$BaseUrl/system/menu/list" },
    @{ name = 'system.dept.list'; method = 'GET'; url = "$BaseUrl/system/dept/list" },
    @{ name = 'system.post.list'; method = 'GET'; url = "$BaseUrl/system/post/list?pageNum=1&pageSize=10" },
    @{ name = 'system.dict.type.list'; method = 'GET'; url = "$BaseUrl/system/dict/type/list?pageNum=1&pageSize=10" },
    @{ name = 'system.dict.data.type'; method = 'GET'; url = "$BaseUrl/system/dict/data/type/sys_yes_no" },
    @{ name = 'system.config.list'; method = 'GET'; url = "$BaseUrl/system/config/list?pageNum=1&pageSize=10" },
    @{ name = 'system.notice.list'; method = 'GET'; url = "$BaseUrl/system/notice/list?pageNum=1&pageSize=10" },
    @{ name = 'monitor.operlog.list'; method = 'GET'; url = "$BaseUrl/monitor/operlog/list?pageNum=1&pageSize=10" },
    @{ name = 'monitor.logininfor.list'; method = 'GET'; url = "$BaseUrl/monitor/logininfor/list?pageNum=1&pageSize=10" },
    @{ name = 'quant.dashboard.summary'; method = 'GET'; url = "$BaseUrl/quant/dashboard/summary" },
    @{ name = 'quant.data.signals'; method = 'GET'; url = "$BaseUrl/quant/data/signals?signalDate=2026-05-04" },
    @{ name = 'quant.data.positions'; method = 'GET'; url = "$BaseUrl/quant/data/positions" },
    @{ name = 'quant.data.strategyLogs'; method = 'GET'; url = "$BaseUrl/quant/data/strategyLogs?limit=20" },
    @{ name = 'quant.data.marketStatus'; method = 'GET'; url = "$BaseUrl/quant/data/marketStatus" },
    @{ name = 'quant.data.indexValuations'; method = 'GET'; url = "$BaseUrl/quant/data/indexValuations?limit=20" },
    @{ name = 'quant.data.strategySwitchAudits'; method = 'GET'; url = "$BaseUrl/quant/data/strategySwitchAudits?limit=20" },
    @{ name = 'quant.data.executionFeedbackSummary'; method = 'GET'; url = "$BaseUrl/quant/data/executionFeedbackSummary" },
    @{ name = 'quant.data.executionReconciliationSummary'; method = 'GET'; url = "$BaseUrl/quant/data/executionReconciliationSummary" },
    @{ name = 'quant.data.executionFeedbackDetails'; method = 'GET'; url = "$BaseUrl/quant/data/executionFeedbackDetails?limit=20" },
    @{ name = 'quant.data.executionMatchCandidates'; method = 'GET'; url = "$BaseUrl/quant/data/executionMatchCandidates?executionRecordId=0&limit=5" },
    @{ name = 'quant.data.positionSyncResult'; method = 'GET'; url = "$BaseUrl/quant/data/positionSyncResult" },
    @{ name = 'quant.data.canaryLatest'; method = 'GET'; url = "$BaseUrl/quant/data/canaryLatest" },
    @{ name = 'quant.data.strategyCapabilities'; method = 'GET'; url = "$BaseUrl/quant/data/strategyCapabilities" },
    @{ name = 'quant.data.shadowCompare'; method = 'GET'; url = "$BaseUrl/quant/data/shadowCompare?baselineStrategyId=1&candidateStrategyId=2&months=6" },
    @{ name = 'quant.data.executionRecords'; method = 'GET'; url = "$BaseUrl/quant/data/executionRecords?limit=20" },
    @{ name = 'quant.data.jobBatches'; method = 'GET'; url = "$BaseUrl/quant/data/jobBatches?limit=20" },
    @{ name = 'quant.data.jobSteps'; method = 'GET'; url = "$BaseUrl/quant/data/jobSteps?batchId=0" },
    @{ name = 'quant.data.asyncJobs'; method = 'GET'; url = "$BaseUrl/quant/data/asyncJobs?limit=20" },
    @{ name = 'quant.jobs.shadowCompare'; method = 'POST'; url = "$BaseUrl/quant/jobs/shadowCompare?baselineStrategyId=1&candidateStrategyId=2&months=1" },
    @{ name = 'quant.jobs.evaluateExecutionFeedback'; method = 'POST'; url = "$BaseUrl/quant/jobs/evaluateExecutionFeedback?graceDays=1" },
    @{ name = 'quant.jobs.canaryEvaluate'; method = 'POST'; url = "$BaseUrl/quant/jobs/canaryEvaluate?baselineStrategyId=1&candidateStrategyId=2&months=1" }
)

$results = foreach ($case in $cases) {
    Invoke-Api -Name $case.name -Method $case.method -Url $case.url -Headers $authHeaders
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$templateCsv = Join-Path $repoRoot 'ruoyi-ui\public\templates\execution-import-template.csv'
$smokeUploadCsv = Join-Path $repoRoot 'runtime-logs\execution-import-smoke.csv'
New-ExecutionImportSmokeCsv -TemplatePath $templateCsv -OutputPath $smokeUploadCsv
$uploadUrl = "$BaseUrl/quant/jobs/importExecutionsUpload?strategyId=1"
$results += Invoke-ApiMultipart -Name 'quant.jobs.importExecutionsUpload' -Url $uploadUrl -Headers $authHeaders -FilePath $smokeUploadCsv -FormFields @{}

$submitBody = @{
    requestedMode = 'async'
    strategyBacktestStartDate = '2023-01-01'
    portfolioTotalCapital = 100000
    actor = 'api-smoke'
} | ConvertTo-Json -Compress
$jobSubmit = Invoke-Api -Name 'quant.jobs.runPortfolio.async' -Method 'POST' -Url "$BaseUrl/quant/jobs/runPortfolio" -Headers $authHeaders -Body $submitBody
$results += $jobSubmit
if ($jobSubmit.pass -and $null -ne $jobSubmit.data -and $null -ne $jobSubmit.data.data -and $jobSubmit.data.data.jobId) {
    $jobId = [int64]$jobSubmit.data.data.jobId
    $results += Invoke-Api -Name 'quant.jobs.status' -Method 'GET' -Url "$BaseUrl/quant/jobs/status/$jobId" -Headers $authHeaders
    $results += Invoke-Api -Name 'quant.data.asyncJobShards' -Method 'GET' -Url "$BaseUrl/quant/data/asyncJobShards?jobId=$jobId" -Headers $authHeaders
    $results += Invoke-Api -Name 'quant.data.asyncJobResults' -Method 'GET' -Url "$BaseUrl/quant/data/asyncJobResults?jobId=$jobId&limit=20" -Headers $authHeaders
}

$failed = @($results | Where-Object { -not $_.pass })
$summary = [pscustomobject]@{
    total  = $results.Count
    pass   = @($results | Where-Object { $_.pass }).Count
    fail   = $failed.Count
    failed = $failed
}

$summary | ConvertTo-Json -Depth 6

if ($summary.fail -gt 0) {
    exit 2
}
