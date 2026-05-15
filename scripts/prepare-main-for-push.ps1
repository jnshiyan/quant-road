param(
    [string]$MainBranch = 'main',
    [switch]$RunVerification,
    [switch]$Push
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$fullRegressionScript = Join-Path $repoRoot 'scripts\full-regression.ps1'

function Invoke-Git {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )

    $output = & git @Args 2>&1
    if ($LASTEXITCODE -ne 0) {
        $message = @($output) -join [Environment]::NewLine
        throw "git $($Args -join ' ') failed.`n$message"
    }
    return @($output)
}

function Get-TrimmedLines {
    param([string[]]$Lines)

    return @($Lines | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

Push-Location $repoRoot
try {
    $currentBranch = (Invoke-Git rev-parse --abbrev-ref HEAD | Select-Object -First 1).Trim()
    $statusLines = Get-TrimmedLines (Invoke-Git status --short)
    $unmergedBranches = Get-TrimmedLines (Invoke-Git branch --format '%(refname:short)' --no-merged $MainBranch) |
        Where-Object { $_ -ne $MainBranch }
    $worktrees = Get-TrimmedLines (Invoke-Git worktree list)

    Write-Host "Repo root: $repoRoot" -ForegroundColor Cyan
    Write-Host "Current branch: $currentBranch" -ForegroundColor Cyan

    if ($currentBranch -ne $MainBranch) {
        throw "Root workspace must be on '$MainBranch' before push preparation. Current branch: '$currentBranch'."
    }

    if ($statusLines.Count -gt 0) {
        Write-Host 'Working tree is not clean:' -ForegroundColor Yellow
        $statusLines | ForEach-Object { Write-Host " - $_" }
        throw "Refusing to prepare '$MainBranch' for push while the root workspace has uncommitted changes."
    }

    if ($unmergedBranches.Count -gt 0) {
        Write-Host "Local branches still not merged into '$MainBranch':" -ForegroundColor Yellow
        $unmergedBranches | ForEach-Object { Write-Host " - $_" }
        throw "Refusing to mark '$MainBranch' ready because some local branches are not yet merged."
    }

    Write-Host "All local branches are merged into '$MainBranch'." -ForegroundColor Green
    Write-Host 'Known worktrees:' -ForegroundColor Cyan
    $worktrees | ForEach-Object { Write-Host " - $_" }

    if ($RunVerification) {
        Write-Host 'Running full regression before push...' -ForegroundColor Cyan
        powershell -NoProfile -ExecutionPolicy Bypass -File $fullRegressionScript
        if ($LASTEXITCODE -ne 0) {
            throw 'Full regression failed. Main is not ready for push.'
        }
        Write-Host 'Full regression passed.' -ForegroundColor Green
    }

    if ($Push) {
        Write-Host "Pushing '$MainBranch' to origin..." -ForegroundColor Cyan
        & git push -u origin $MainBranch
        if ($LASTEXITCODE -ne 0) {
            throw "Push to origin/$MainBranch failed."
        }
        Write-Host "Push to origin/$MainBranch completed." -ForegroundColor Green
    }

    [pscustomobject]@{
        repoRoot = $repoRoot
        mainBranch = $MainBranch
        currentBranch = $currentBranch
        workingTreeClean = ($statusLines.Count -eq 0)
        unmergedBranchCount = $unmergedBranches.Count
        worktreeCount = $worktrees.Count
        verificationRan = [bool]$RunVerification
        pushRequested = [bool]$Push
        ready = $true
    } | ConvertTo-Json -Depth 4
}
finally {
    Pop-Location
}
