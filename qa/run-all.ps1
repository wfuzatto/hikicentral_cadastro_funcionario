[CmdletBinding()]
param([switch]$SkipDevice)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$reportDir = Join-Path $PSScriptRoot 'reports'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = Join-Path $reportDir "run-$stamp.log"
Start-Transcript -Path $log | Out-Null
try {
    Push-Location $root
    try {
        $gradle = if (Get-Command gradle -ErrorAction SilentlyContinue) { 'gradle' } else { $null }
        if (-not $gradle) {
            $localGradle = Join-Path (Split-Path -Parent $root) 'android-build-tools\gradle89\bin\gradle.bat'
            if (Test-Path $localGradle) { $gradle = $localGradle }
        }
        if (-not $gradle) { throw 'Gradle was not found. Install Gradle or open this project in Android Studio.' }
        if (-not $env:JAVA_HOME) {
            $localJdk = Join-Path (Split-Path -Parent $root) 'android-build-tools\jdk17'
            if (Test-Path (Join-Path $localJdk 'bin\java.exe')) { $env:JAVA_HOME = $localJdk }
        }
        if (-not $env:ANDROID_HOME) {
            $localSdk = Join-Path (Split-Path -Parent $root) 'android-build-tools\sdk'
            if (Test-Path $localSdk) { $env:ANDROID_HOME = $localSdk; $env:ANDROID_SDK_ROOT = $localSdk }
        }
        if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
            $localAdb = Join-Path (Split-Path -Parent $root) 'android-platform-tools\platform-tools\adb.exe'
            if (Test-Path $localAdb) { $env:PATH = (Split-Path $localAdb) + ';' + $env:PATH }
        }
        & $gradle testDebugUnitTest assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
        if (-not $SkipDevice) {
            & (Join-Path $PSScriptRoot 'test-connectivity.ps1')
            & (Join-Path $PSScriptRoot 'install-apk.ps1')
            & (Join-Path $PSScriptRoot 'test-ui.ps1')
            & (Join-Path $PSScriptRoot 'test-camera.ps1')
            & (Join-Path $PSScriptRoot 'collect-logs.ps1')
        }
        Write-Host "QA complete. Transcript: $log"
    } finally { Pop-Location }
} catch {
    Write-Error $_
    exit 1
} finally { Stop-Transcript | Out-Null }
