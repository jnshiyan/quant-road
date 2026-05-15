Set-StrictMode -Version Latest

function Resolve-ServicePath {
    param(
        [string]$RepoRoot,
        [string]$RelativePath
    )

    if ([System.IO.Path]::IsPathRooted($RelativePath)) {
        return [System.IO.Path]::GetFullPath($RelativePath)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $RelativePath))
}

function Get-PathVariants {
    param(
        [string]$RepoRoot,
        [string]$RelativePath
    )

    $absolutePath = Resolve-ServicePath -RepoRoot $RepoRoot -RelativePath $RelativePath
    $absoluteBackslash = $absolutePath.Replace('/', '\')
    $absoluteSlash = $absolutePath.Replace('\', '/')
    $relativeRaw = $RelativePath
    $relativeBackslash = $RelativePath.Replace('/', '\')
    $relativeSlash = $RelativePath.Replace('\', '/')

    return @($absoluteBackslash, $absoluteSlash, $relativeRaw, $relativeBackslash, $relativeSlash) |
        Where-Object { $_ } |
        Select-Object -Unique
}

function Test-CommandLineContainsPath {
    param(
        [string]$CommandLine,
        [string]$RepoRoot,
        [string]$RelativePath
    )

    if (-not $CommandLine) {
        return $false
    }

    foreach ($variant in (Get-PathVariants -RepoRoot $RepoRoot -RelativePath $RelativePath)) {
        if ($CommandLine.Contains($variant)) {
            return $true
        }
    }
    return $false
}

function Test-RuoyiAdminCommandLine {
    param(
        [string]$CommandLine,
        [string]$JarPath,
        [string]$RepoRoot
    )

    if (-not $CommandLine) {
        return $false
    }

    return (Test-CommandLineContainsPath -CommandLine $CommandLine -RepoRoot $RepoRoot -RelativePath $JarPath) `
        -and $CommandLine.Contains(' -jar ')
}

function Test-RuoyiUiCommandLine {
    param(
        [string]$CommandLine,
        [string]$UiDir,
        [string]$RepoRoot
    )

    if (-not $CommandLine) {
        return $false
    }

    $containsUiPath = Test-CommandLineContainsPath -CommandLine $CommandLine -RepoRoot $RepoRoot -RelativePath $UiDir
    $isVueDevServer = $CommandLine.Contains('vue-cli-service') -and $CommandLine.Contains('serve')
    return $containsUiPath -and $isVueDevServer
}

function Test-QuantWorkerCommandLine {
    param(
        [string]$CommandLine,
        [string]$PythonDir,
        [string]$RepoRoot
    )

    if (-not $CommandLine) {
        return $false
    }

    $isQuantWorker = $CommandLine.Contains('quant_road') -and $CommandLine.Contains('run-async-worker')
    $isSupervisor = $CommandLine.Contains('run-quant-worker-loop.ps1')
    $containsPythonDir = Test-CommandLineContainsPath -CommandLine $CommandLine -RepoRoot $RepoRoot -RelativePath $PythonDir
    if ($containsPythonDir -and $isQuantWorker) {
        return $true
    }

    if ($isSupervisor) {
        return $true
    }

    return (-not $containsPythonDir) -and $isQuantWorker
}

function Get-ProcessCommandLine {
    param($Process)
    return [string]$Process.CommandLine
}

function Get-RuoyiAdminJarProcesses {
    param(
        [string]$JarPath,
        [string]$RepoRoot
    )

    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -eq 'java.exe' -and
            (Test-RuoyiAdminCommandLine -CommandLine (Get-ProcessCommandLine $_) -JarPath $JarPath -RepoRoot $RepoRoot)
        }
}

function Get-RuoyiUiServeProcesses {
    param(
        [string]$UiDir,
        [string]$RepoRoot
    )

    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -eq 'node.exe' -and
            (Test-RuoyiUiCommandLine -CommandLine (Get-ProcessCommandLine $_) -UiDir $UiDir -RepoRoot $RepoRoot)
        }
}

function Get-QuantWorkerProcesses {
    param(
        [string]$PythonDir,
        [string]$RepoRoot
    )

    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -in @('python.exe', 'powershell.exe') -and
            (Test-QuantWorkerCommandLine -CommandLine (Get-ProcessCommandLine $_) -PythonDir $PythonDir -RepoRoot $RepoRoot)
        }
}

function Get-ProcessIndex {
    $index = @{}
    foreach ($proc in (Get-CimInstance Win32_Process)) {
        $index[[int]$proc.ProcessId] = $proc
    }
    return $index
}

function Get-ParentProcessIds {
    param(
        [int[]]$ChildProcessIds,
        [string[]]$AllowedNames = @('node.exe')
    )

    $index = Get-ProcessIndex
    $parentIds = @()
    foreach ($id in $ChildProcessIds) {
        if (-not $index.ContainsKey($id)) {
            continue
        }
        $parentId = [int]$index[$id].ParentProcessId
        if ($parentId -le 0 -or -not $index.ContainsKey($parentId)) {
            continue
        }
        if ($AllowedNames -contains $index[$parentId].Name) {
            $parentIds += $parentId
        }
    }
    return @($parentIds | Select-Object -Unique)
}

function Stop-ProcessIds {
    param([int[]]$ProcessIds)

    $ids = @($ProcessIds | Where-Object { $_ -gt 0 } | Select-Object -Unique)
    if (-not $ids) {
        return @()
    }

    Stop-Process -Id $ids -Force
    foreach ($id in $ids) {
        Wait-Process -Id $id -ErrorAction SilentlyContinue
    }
    return $ids
}

function Test-HttpEndpoint {
    param(
        [string]$Url,
        [string]$Accept = 'text/html'
    )

    try {
        $null = Invoke-WebRequest -Uri $Url -Headers @{ Accept = $Accept } -UseBasicParsing -TimeoutSec 5
        return $true
    } catch {
        return $false
    }
}

function Wait-HttpEndpoint {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 90,
        [string]$Accept = 'text/html'
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpEndpoint -Url $Url -Accept $Accept) {
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Endpoint not reachable within ${TimeoutSeconds}s: $Url"
}

function Resolve-RedirectPath {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $Path
    }

    try {
        Remove-Item -LiteralPath $Path -Force
        return $Path
    } catch {
        $directory = Split-Path -Parent $Path
        $fileName = [System.IO.Path]::GetFileNameWithoutExtension($Path)
        $extension = [System.IO.Path]::GetExtension($Path)
        $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        return (Join-Path $directory ("{0}.{1}{2}" -f $fileName, $timestamp, $extension))
    }
}

function Start-LoggedProcess {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$StdoutPath,
        [string]$StderrPath
    )

    $stdoutRedirectPath = Resolve-RedirectPath -Path $StdoutPath
    $stderrRedirectPath = Resolve-RedirectPath -Path $StderrPath

    return Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdoutRedirectPath `
        -RedirectStandardError $stderrRedirectPath `
        -WindowStyle Hidden `
        -PassThru
}

function Wait-ProcessStable {
    param(
        [int]$ProcessId,
        [int]$StableSeconds = 3
    )

    Start-Sleep -Seconds $StableSeconds
    return [bool](Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}
