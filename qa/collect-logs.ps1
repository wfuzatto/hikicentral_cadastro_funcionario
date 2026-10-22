[CmdletBinding()]
param([string]$Serial)
$ErrorActionPreference='Stop'
$adb=(Get-Command adb -ErrorAction SilentlyContinue).Source
if(-not $adb){throw 'adb not found in PATH.'}
$devices=@(& $adb devices | Select-String '^\S+\s+device$')
if($Serial){$devices=@($devices|Where-Object{$_.Line -match "^$([regex]::Escape($Serial))\s+device$"})}
if($devices.Count -ne 1){throw 'Expected exactly one authorized Android device.'}
$id=($devices[0].Line -split '\s+')[0]
$dir=Join-Path $PSScriptRoot ('reports\logs-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $dir|Out-Null
& $adb -s $id logcat -d -v time | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime| E ' | Set-Content (Join-Path $dir 'errors.log')
Write-Host "Filtered error log saved to $dir. Review before sharing; logs may contain environment details."
