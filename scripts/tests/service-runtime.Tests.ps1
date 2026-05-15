$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $here '..\..'))
. (Join-Path $repoRoot 'scripts\lib\service-runtime.ps1')

Describe 'service-runtime command line matching' {
    It 'matches ruoyi-admin jar command line with relative jar path' {
        $commandLine = '"D:\program\Java\jdk-17.0.14\bin\java.exe" -jar ruoyi-admin/target/ruoyi-admin.jar'

        Test-RuoyiAdminCommandLine -CommandLine $commandLine -JarPath 'ruoyi-admin/target/ruoyi-admin.jar' -RepoRoot $repoRoot | Should Be $true
    }

    It 'matches ruoyi-ui vue-cli-service dev server command line' {
        $commandLine = '"node" "D:\hundsun-workspaces\itellij-space\git-workspace\quant-road\ruoyi-ui\node_modules\.bin\..\@vue\cli-service\bin\vue-cli-service.js" serve'

        Test-RuoyiUiCommandLine -CommandLine $commandLine -UiDir 'ruoyi-ui' -RepoRoot $repoRoot | Should Be $true
    }

    It 'does not treat unrelated codex node process as ruoyi-ui dev server' {
        $commandLine = '"D:\Program Files\nodejs\node.exe" C:\Users\shizm21605\AppData\Roaming\npm\node_modules\@openai\codex\bin\codex.js -s danger-full-access'

        Test-RuoyiUiCommandLine -CommandLine $commandLine -UiDir 'ruoyi-ui' -RepoRoot $repoRoot | Should Be $false
    }

    It 'matches quant worker command line' {
        $commandLine = '"C:\Python313\python.exe" -m quant_road run-async-worker --worker-id ruoyi-web-worker'

        Test-QuantWorkerCommandLine -CommandLine $commandLine -PythonDir 'python' -RepoRoot $repoRoot | Should Be $true
    }

    It 'matches quant worker supervisor powershell command line' {
        $commandLine = 'powershell -NoProfile -ExecutionPolicy Bypass -File D:\hundsun-workspaces\itellij-space\git-workspace\quant-road\scripts\run-quant-worker-loop.ps1 -WorkerId ruoyi-web-worker'

        Test-QuantWorkerCommandLine -CommandLine $commandLine -PythonDir 'python' -RepoRoot $repoRoot | Should Be $true
    }

    It 'does not treat unrelated python process as quant worker' {
        $commandLine = '"D:\Program Files\PostgreSQL\18\pgAdmin 4\python\python.exe" -s "D:\Program Files\PostgreSQL\18\pgAdmin 4\web\pgAdmin4.py"'

        Test-QuantWorkerCommandLine -CommandLine $commandLine -PythonDir 'python' -RepoRoot $repoRoot | Should Be $false
    }
}
