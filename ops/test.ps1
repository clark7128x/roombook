param(
    [ValidatePattern('^[A-Za-z0-9_.*,#-]*$')]
    [string]$Test = '',
    [ValidatePattern('^target/[A-Za-z0-9._-]+\.log$')]
    [string]$Log = 'target/test.log'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'E:\Projects\roombook-tools\.tools\jdk-21.0.12.1+1'
$mavenCommand = 'E:\Projects\roombook-tools\.tools\apache-maven-3.10.0\bin\mvn.cmd'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Push-Location -LiteralPath $projectRoot
try {
    $mavenArguments = @('-o', 'test')
    if ($Test) { $mavenArguments += "-Dtest=$Test" }
    & $mavenCommand @mavenArguments *> $Log
    $testExitCode = $LASTEXITCODE
    Get-Content -LiteralPath $Log -Tail 12
    exit $testExitCode
} finally {
    Pop-Location
}
