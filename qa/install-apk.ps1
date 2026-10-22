[CmdletBinding()]
param([string]$Serial)
$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { throw 'adb not found in PATH. Install Android platform-tools.' }
$devices = @(& $adb devices | Select-String '^\S+\s+device$')
if ($Serial) { $devices = @($devices | Where-Object { $_.Line -match "^$([regex]::Escape($Serial))\s+device$" }) }
if ($devices.Count -ne 1) { throw "Expected exactly one authorized device, found $($devices.Count). Check 'adb devices -l'. No app data was cleared." }
$serialFound = ($devices[0].Line -split '\s+')[0]
$apk = Join-Path (Split-Path -Parent $PSScriptRoot) 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK not found: $apk. Run Gradle assembleDebug first." }
& $adb -s $serialFound install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK update/install failed. Existing installation and app data were left intact; check signing compatibility.' }
Write-Host "Installed APK as update on $serialFound. Existing app data was preserved."
