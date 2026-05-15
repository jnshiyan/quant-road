param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$JarPath = 'ruoyi-admin/target/ruoyi-admin.jar',
    [string]$JavaPath = 'java',
    [switch]$SkipTests,
    [switch]$SkipPackage,
    [switch]$StopOnly,
    [switch]$StartOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
$runtimeDir = Join-Path $repoRoot 'runtime-logs'
. (Join-Path $repoRoot 'scripts\lib\service-runtime.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-ExecutableBootJar {
    $jarFullPath = Resolve-ServicePath -RepoRoot $repoRoot -RelativePath $JarPath
    if (-not (Test-Path $jarFullPath)) {
        throw "Jar not found: $jarFullPath"
    }

    $zip = [System.IO.Compression.ZipFile]::OpenRead($jarFullPath)
    try {
        $hasBootInf = $zip.Entries | Where-Object { $_.FullName -like 'BOOT-INF/*' } | Select-Object -First 1
        if (-not $hasBootInf) {
            throw "Jar is not a Spring Boot executable archive: $jarFullPath"
        }
    } finally {
        $zip.Dispose()
    }
}

function Stop-RuoyiAdmin {
    $processes = @(Get-RuoyiAdminJarProcesses -JarPath $JarPath -RepoRoot $repoRoot)
    if (-not $processes) {
        Write-Host 'No running ruoyi-admin jar process found.' -ForegroundColor DarkGray
        return @()
    }

    $ids = @($processes | Select-Object -ExpandProperty ProcessId)
    Write-Host "Stopping ruoyi-admin jar process(es): $($ids -join ', ')" -ForegroundColor Yellow
    return @(Stop-ProcessIds -ProcessIds $ids)
}

function Start-RuoyiAdmin {
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
    $stdout = Join-Path $runtimeDir 'ruoyi-admin.stdout.log'
    $stderr = Join-Path $runtimeDir 'ruoyi-admin.stderr.log'
    $jarFullPath = Resolve-ServicePath -RepoRoot $repoRoot -RelativePath $JarPath

    Write-Host "Starting ruoyi-admin jar: $jarFullPath" -ForegroundColor Cyan
    $proc = Start-LoggedProcess `
        -FilePath $JavaPath `
        -ArgumentList @('-jar', $jarFullPath) `
        -WorkingDirectory $repoRoot `
        -StdoutPath $stdout `
        -StderrPath $stderr

    Write-Host "Started pid=$($proc.Id)" -ForegroundColor Green
    Wait-HttpEndpoint -Url $BaseUrl
    return $proc
}

if ($StopOnly -and $StartOnly) {
    throw 'StopOnly and StartOnly cannot be used together.'
}

if (-not $StartOnly -and -not $SkipTests) {
    Write-Host 'Running targeted backend tests...' -ForegroundColor Cyan
    mvn -pl ruoyi-admin -am "-Dtest=QuantRoadDataControllerTest,QuantDispatchDetailServiceTest" -DfailIfNoTests=false test
    if ($LASTEXITCODE -ne 0) {
        throw "Targeted backend tests failed with exit code $LASTEXITCODE"
    }
}

if (-not $StartOnly) {
    Stop-RuoyiAdmin | Out-Null
}

if ($StopOnly) {
    Write-Host 'ruoyi-admin stop-only completed.' -ForegroundColor Green
    return
}

if (-not $SkipPackage) {
    Write-Host 'Packaging ruoyi-admin executable jar...' -ForegroundColor Cyan
    mvn -pl ruoyi-admin -am -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Package failed with exit code $LASTEXITCODE"
    }
}

Assert-ExecutableBootJar
Start-RuoyiAdmin | Out-Null
Write-Host 'ruoyi-admin restart completed successfully.' -ForegroundColor Green
