param(
    [string]$ApiBaseUrl = 'http://localhost:8080',
    [string]$UiBaseUrl = 'http://localhost:8081',
    [string]$Username = 'admin',
    [string]$Password = 'admin123',
    [switch]$SkipHeaded
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeDir = Join-Path $repoRoot 'runtime-logs'
$reportPath = Join-Path $runtimeDir 'full-regression-report.json'

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

$results = New-Object System.Collections.Generic.List[Object]

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Workdir,
        [scriptblock]$Action
    )

    Write-Host ">>> [$Name] START" -ForegroundColor Cyan
    $start = Get-Date
    $status = 'PASS'
    $errorMessage = ''
    $exitCode = 0

    Push-Location $Workdir
    try {
        & $Action
        if ($null -ne $LASTEXITCODE) {
            $exitCode = [int]$LASTEXITCODE
            if ($exitCode -ne 0) {
                throw "Exit code $exitCode"
            }
        }
    } catch {
        $status = 'FAIL'
        $errorMessage = $_.Exception.Message
        if ($exitCode -eq 0) { $exitCode = 1 }
    } finally {
        Pop-Location
    }

    $end = Get-Date
    $durationSec = [Math]::Round((New-TimeSpan -Start $start -End $end).TotalSeconds, 2)
    $stepResult = [pscustomobject]@{
        name       = $Name
        status     = $status
        exitCode   = $exitCode
        startedAt  = $start.ToString('o')
        finishedAt = $end.ToString('o')
        durationSec = $durationSec
        workdir    = $Workdir
        error      = $errorMessage
    }
    $results.Add($stepResult)

    if ($status -eq 'PASS') {
        Write-Host ">>> [$Name] PASS (${durationSec}s)" -ForegroundColor Green
    } else {
        Write-Host ">>> [$Name] FAIL (${durationSec}s): $errorMessage" -ForegroundColor Red
    }
}

function Assert-ExecutableBootJar {
    param(
        [string]$JarPath
    )

    if (-not (Test-Path $JarPath)) {
        throw "Packaged jar not found: $JarPath"
    }

    $entries = & jar tf $JarPath
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect packaged jar: $JarPath"
    }

    $isBootJar = $entries | Where-Object {
        $_ -like 'BOOT-INF/*' -or $_ -like 'org/springframework/boot/loader/*'
    } | Select-Object -First 1

    if (-not $isBootJar) {
        throw "Packaged jar is not an executable Spring Boot jar: $JarPath. Repackage was skipped or failed."
    }
}

function Get-RuoyiAdminJarProcesses {
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq 'java.exe' -and
        $_.CommandLine -match 'ruoyi-admin[\\/]+target[\\/]+ruoyi-admin\.jar'
    }
}

function Test-HttpEndpoint {
    param(
        [string]$Url
    )

    try {
        $null = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
        return $true
    } catch {
        return $false
    }
}

function Wait-HttpEndpoint {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpEndpoint -Url $Url) {
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Endpoint not reachable within ${TimeoutSeconds}s: $Url"
}

function Stop-RuoyiAdminJarProcesses {
    $processes = @(Get-RuoyiAdminJarProcesses)
    if (-not $processes) {
        return @()
    }

    $ids = @($processes | Select-Object -ExpandProperty ProcessId)
    Write-Host ">>> [java.package] stop running ruoyi-admin jar process(es): $($ids -join ', ')" -ForegroundColor Yellow
    Stop-Process -Id $ids -Force
    foreach ($id in $ids) {
        Wait-Process -Id $id -ErrorAction SilentlyContinue
    }
    return $ids
}

function Start-RuoyiAdminJar {
    if (Get-RuoyiAdminJarProcesses | Select-Object -First 1) {
        return
    }

    $outLog = Join-Path $runtimeDir 'ruoyi-admin-regression.out.log'
    $errLog = Join-Path $runtimeDir 'ruoyi-admin-regression.err.log'
    $proc = Start-Process `
        -FilePath 'java' `
        -ArgumentList '-jar', $adminJar `
        -WorkingDirectory $repoRoot `
        -WindowStyle Hidden `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru
    $script:startedAdminProcessId = $proc.Id
    Write-Host ">>> [api] started ruoyi-admin jar pid=$($proc.Id)" -ForegroundColor Yellow
}

function Ensure-ApiAvailable {
    if (Test-HttpEndpoint -Url $ApiBaseUrl) {
        return
    }

    Start-RuoyiAdminJar
    Wait-HttpEndpoint -Url $ApiBaseUrl
}

$uiDir = Join-Path $repoRoot 'ruoyi-ui'
$pythonDir = Join-Path $repoRoot 'python'
$apiSmokeScript = Join-Path $repoRoot 'scripts\api-smoke.ps1'
$asyncBenchmarkScript = Join-Path $repoRoot 'scripts\quant-async-benchmark.ps1'
$executionBenchmarkScript = Join-Path $repoRoot 'scripts\execution-reconciliation-benchmark.ps1'
$adminJar = Join-Path $repoRoot 'ruoyi-admin\target\ruoyi-admin.jar'
$script:startedAdminProcessId = $null
$script:apiWasInitiallyReachable = Test-HttpEndpoint -Url $ApiBaseUrl

Invoke-Step -Name 'java.compile' -Workdir $repoRoot -Action {
    mvn -pl ruoyi-admin -am -DskipTests compile
}

Invoke-Step -Name 'java.package' -Workdir $repoRoot -Action {
    $stoppedAdminProcessIds = @(Stop-RuoyiAdminJarProcesses)
    $packageLog = mvn -pl ruoyi-admin -am -DskipTests package 2>&1
    $packageLog | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        $packageText = ($packageLog | Out-String)
        $jarLocked =
            ($packageText -match 'Unable to rename') -or
            ($packageText -match 'being used by another process') -or
            ($packageText -match 'used by another process') -or
            ($packageText -match 'Access is denied')
        if ($jarLocked) {
            throw "java.package failed because ruoyi-admin.jar is locked by a running process. Stop the running admin jar and re-run. The old repackage-skip fallback was removed because it produces a non-executable plain jar."
        }
        throw "Exit code $LASTEXITCODE"
    }
    Assert-ExecutableBootJar -JarPath $adminJar
    if ($stoppedAdminProcessIds.Count -gt 0) {
        Start-RuoyiAdminJar
        Wait-HttpEndpoint -Url $ApiBaseUrl
    }
}

Invoke-Step -Name 'python.unit' -Workdir $pythonDir -Action {
    $env:PYTHONPATH = 'src'
    python -m unittest discover -s tests -p "test_*.py"
}

Invoke-Step -Name 'ui.build.prod' -Workdir $uiDir -Action {
    npm run build:prod
}

Invoke-Step -Name 'ui.playwright.headless' -Workdir $uiDir -Action {
    Ensure-ApiAvailable
    $env:PW_BASE_URL = $UiBaseUrl
    $env:PW_USERNAME = $Username
    $env:PW_PASSWORD = $Password
    npm run smoke:e2e
}

if (-not $SkipHeaded) {
    Invoke-Step -Name 'ui.playwright.headed' -Workdir $uiDir -Action {
        Ensure-ApiAvailable
        $env:PW_BASE_URL = $UiBaseUrl
        $env:PW_USERNAME = $Username
        $env:PW_PASSWORD = $Password
        npm run smoke:e2e:headed
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ui.playwright.headed failed, retry once due possible GUI race condition." -ForegroundColor Yellow
            Start-Sleep -Seconds 2
            npm run smoke:e2e:headed
        }
    }
}

Invoke-Step -Name 'api.smoke' -Workdir $repoRoot -Action {
    Ensure-ApiAvailable
    powershell -NoProfile -ExecutionPolicy Bypass -File $apiSmokeScript -BaseUrl $ApiBaseUrl -Username $Username -Password $Password
}

Invoke-Step -Name 'api.executionReconciliationBenchmark' -Workdir $repoRoot -Action {
    Ensure-ApiAvailable
    powershell -NoProfile -ExecutionPolicy Bypass -File $executionBenchmarkScript -BaseUrl $ApiBaseUrl -Username $Username -Password $Password
}

Invoke-Step -Name 'api.quantAsyncBenchmark' -Workdir $repoRoot -Action {
    Ensure-ApiAvailable
    powershell -NoProfile -ExecutionPolicy Bypass -File $asyncBenchmarkScript -BaseUrl $ApiBaseUrl -Username $Username -Password $Password
}

if ($script:startedAdminProcessId -and -not $script:apiWasInitiallyReachable) {
    Stop-Process -Id $script:startedAdminProcessId -Force -ErrorAction SilentlyContinue
}

$failed = @($results | Where-Object { $_.status -ne 'PASS' })
$summary = [pscustomobject]@{
    startedAt = ($results[0].startedAt)
    finishedAt = (Get-Date).ToString('o')
    total = $results.Count
    pass = @($results | Where-Object { $_.status -eq 'PASS' }).Count
    fail = $failed.Count
    failed = $failed
    steps = $results
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath -Encoding UTF8
Write-Host ">>> Report: $reportPath" -ForegroundColor Yellow
$summary | ConvertTo-Json -Depth 8

if ($summary.fail -gt 0) {
    exit 2
}
