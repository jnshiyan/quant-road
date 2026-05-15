param(
    [switch]$WhatIf
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$targets = @(
    (Join-Path $repoRoot 'runtime-logs\*'),
    (Join-Path $repoRoot '.tmp\*')
)

$worktreeRoot = Join-Path $repoRoot '.worktrees'
if (Test-Path $worktreeRoot) {
    Get-ChildItem -Path $worktreeRoot -Directory | ForEach-Object {
        $targets += (Join-Path $_.FullName 'runtime-logs\*')
        $targets += (Join-Path $_.FullName 'ruoyi-ui\node_modules.__broken__')
    }
}

$removed = @()
$skipped = @()
foreach ($target in $targets) {
    $resolvedParent = Split-Path -Parent $target
    if (-not (Test-Path $resolvedParent)) {
        continue
    }
    if ($WhatIf) {
        Write-Host "[WhatIf] Would remove: $target"
        continue
    }
    Get-ChildItem -Path $target -Force -ErrorAction SilentlyContinue | ForEach-Object {
        $fullPath = $_.FullName
        if (-not $fullPath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove path outside repo root: $fullPath"
        }
        try {
            Remove-Item -LiteralPath $fullPath -Recurse -Force -ErrorAction Stop
            $removed += $fullPath
        }
        catch {
            $skipped += $fullPath
        }
    }
}

if ($WhatIf) {
    Write-Host "Preview complete."
    exit 0
}

if ($removed.Count -eq 0) {
    Write-Host "No local artifacts needed cleanup."
    exit 0
}

Write-Host "Removed local artifacts:"
$removed | ForEach-Object { Write-Host " - $_" }

if ($skipped.Count -gt 0) {
    Write-Host "Skipped because files are in use or still locked:"
    $skipped | ForEach-Object { Write-Host " - $_" }
}
