[CmdletBinding()]
param([string]$Serial)
$ErrorActionPreference='Stop'
$adb=(Get-Command adb -ErrorAction SilentlyContinue).Source
if(-not $adb){throw 'adb not found in PATH.'}
$devices=@(& $adb devices | Select-String '^\S+\s+device$')
if($Serial){$devices=@($devices|Where-Object{$_.Line -match "^$([regex]::Escape($Serial))\s+device$"})}
if($devices.Count -ne 1){throw 'Expected exactly one authorized Android device.'}
$id=($devices[0].Line -split '\s+')[0]
$dir=Join-Path $PSScriptRoot ('reports\camera-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $dir|Out-Null
& $adb -s $id shell am start -n br.com.acquavale.hikicentralcadastro/br.com.acquavale.cadastro.MainActivity|Out-Null
Start-Sleep -Seconds 2
& $adb -s $id shell uiautomator dump /sdcard/camera-launch.xml|Out-Null
& $adb -s $id pull /sdcard/camera-launch.xml (Join-Path $dir 'launch.xml')|Out-Null
[xml]$doc=Get-Content (Join-Path $dir 'launch.xml') -Raw
$button=$doc.SelectSingleNode("//node[@text='TIRAR FOTO']")
if(-not $button){throw 'The camera action was not found in the app UI.'}
$b=[regex]::Match($button.GetAttribute('bounds'),'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
$x=[math]::Floor(([int]$b.Groups[1].Value+[int]$b.Groups[3].Value)/2)
$y=[math]::Floor(([int]$b.Groups[2].Value+[int]$b.Groups[4].Value)/2)
& $adb -s $id shell input tap $x $y
Start-Sleep -Seconds 4
& $adb -s $id shell screencap -p /sdcard/camera-preview.png
& $adb -s $id pull /sdcard/camera-preview.png (Join-Path $dir 'camera-preview.png')|Out-Null
$focus=(& $adb -s $id shell dumpsys window | Select-String 'mCurrentFocus' | Out-String)
if($focus -match 'br\.com\.acquavale\.hikicentralcadastro'){throw 'Camera did not open.'}
# Cancel capture deliberately: no face or other photo is taken.
& $adb -s $id shell input keyevent 4
Start-Sleep -Seconds 2
& $adb -s $id shell uiautomator dump /sdcard/camera-return.xml|Out-Null
& $adb -s $id pull /sdcard/camera-return.xml (Join-Path $dir 'return.xml')|Out-Null
$returnXml=Get-Content (Join-Path $dir 'return.xml') -Raw
if($returnXml -notmatch 'Foto n.*capturada'){throw 'App did not return with the expected canceled-photo state.'}
Write-Host "Camera opened and returned to the app; capture was canceled and no image was taken. Evidence: $dir."
