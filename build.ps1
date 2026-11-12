param(
    [string]$SdkRoot = '',
    [string]$JdkHome = 'C:/Program Files/Java/latest/jdk-21',
    [string]$GradleHome = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$sharedTooling = Join-Path (Split-Path -Parent $projectRoot) 'xbst enhance/.tooling'
if (-not $SdkRoot) { $SdkRoot = Join-Path $sharedTooling 'android-sdk' }
if (-not $GradleHome) { $GradleHome = Join-Path $sharedTooling 'gradle-8.9' }
$previousJava = $env:JAVA_HOME
$previousAndroid = $env:ANDROID_HOME
$previousCache = $env:GRADLE_USER_HOME
$previousUser = $env:ANDROID_USER_HOME
try {
    $env:JAVA_HOME = $JdkHome
    $env:ANDROID_HOME = $SdkRoot
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tooling/gradle-cache'
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tooling/android-user'
    & (Join-Path $projectRoot 'tools/test-core.ps1') -JdkHome $JdkHome
    if ($LASTEXITCODE -ne 0) { throw 'Core checks failed.' }
    & (Join-Path $GradleHome 'bin/gradle.bat') '-Dorg.gradle.jvmargs=' -p $projectRoot :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --max-workers=2
    if ($LASTEXITCODE -ne 0) { throw 'Build or checks failed.' }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousAndroid
    $env:GRADLE_USER_HOME = $previousCache
    $env:ANDROID_USER_HOME = $previousUser
}
