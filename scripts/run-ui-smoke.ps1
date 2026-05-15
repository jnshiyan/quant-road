param(
    [ValidateSet('all', 'quant')]
    [string]$Suite = 'all',
    [switch]$Headed
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$uiDir = Join-Path $repoRoot 'ruoyi-ui'

Push-Location $uiDir
try {
    if ($Suite -eq 'quant') {
        if ($Headed) {
            npm run smoke:e2e:quant:headed
        } else {
            npm run smoke:e2e:quant
        }
    } else {
        if ($Headed) {
            npm run smoke:e2e:headed
        } else {
            npm run smoke:e2e
        }
    }

    if ($LASTEXITCODE -ne 0) {
        throw "UI smoke suite failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
