$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$requirements = Join-Path $repoRoot "python/requirements.txt"

pip install --upgrade pip
pip install -r $requirements
