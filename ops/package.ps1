param([switch]$Online)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'E:\Projects\roombook-tools\.tools\jdk-21.0.12.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Push-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
try {
    $arguments = @('-DskipTests', 'package')
    if (-not $Online) { $arguments = @('-o') + $arguments }
    & 'E:\Projects\roombook-tools\.tools\apache-maven-3.10.0\bin\mvn.cmd' @arguments *> target/package.log
    $result = $LASTEXITCODE
    Get-Content target/package.log -Tail 12
    exit $result
} finally { Pop-Location }
