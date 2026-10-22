[CmdletBinding()]
param([string]$Serial)
$ErrorActionPreference='Stop'
$adb=(Get-Command adb -ErrorAction SilentlyContinue).Source
if(-not $adb){throw 'adb not found in PATH.'}
$devices=@(& $adb devices | Select-String '^\S+\s+device$')
if($Serial){$devices=@($devices|Where-Object{$_.Line -match "^$([regex]::Escape($Serial))\s+device$"})}
if($devices.Count -ne 1){throw "Expected exactly one authorized Android device. Current result: $(& $adb devices -l | Out-String)"}
$id=($devices[0].Line -split '\s+')[0]
$dir=Join-Path $PSScriptRoot ('reports\device-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $dir | Out-Null
& $adb -s $id logcat -c
& $adb -s $id shell am start -n br.com.acquavale.hikicentralcadastro/br.com.acquavale.cadastro.MainActivity
if($LASTEXITCODE -ne 0){throw 'Could not launch the app.'}
Start-Sleep -Seconds 3
& $adb -s $id shell uiautomator dump /sdcard/hikcentral-window.xml | Out-Null
& $adb -s $id pull /sdcard/hikcentral-window.xml (Join-Path $dir 'window.xml') | Out-Null
& $adb -s $id shell screencap -p /sdcard/hikcentral-screen.png
& $adb -s $id pull /sdcard/hikcentral-screen.png (Join-Path $dir 'screen.png') | Out-Null
$xml=Get-Content (Join-Path $dir 'window.xml') -Raw
if($xml -notmatch 'Cadastro rápido|Cadastro de funcionários'){throw 'Expected app labels not found in UIAutomator hierarchy.'}
Write-Host "Basic launch and UI hierarchy check passed. Screenshot and hierarchy: $dir. Interactive flows (camera/settings/form) require additional device automation and configuration."
