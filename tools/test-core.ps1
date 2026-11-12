param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javac = if ($JdkHome) { Join-Path $JdkHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JdkHome) { Join-Path $JdkHome 'bin/java.exe' } else { 'java' }
$output = Join-Path $projectRoot '.tooling/core-checks'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& $javac --release 8 -encoding UTF-8 -d $output (Join-Path $projectRoot 'app/src/main/java/io/github/connectcolours/usage/DailyUsage.java') (Join-Path $PSScriptRoot 'CoreChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed.' }
& $java -cp $output CoreChecks
if ($LASTEXITCODE -ne 0) { throw 'Core checks failed.' }
