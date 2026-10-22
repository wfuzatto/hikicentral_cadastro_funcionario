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
if($xml -notmatch 'package="br.com.acquavale.hikicentralcadastro"'){throw 'Expected application package not found in UIAutomator hierarchy.'}
[xml]$doc=$xml
$gear=$doc.SelectSingleNode("//node[@content-desc='Configurar servidor HikCentral']")
if(-not $gear){throw 'Accessible settings control was not found.'}
if($gear.GetAttribute('clickable') -ne 'true'){throw 'Settings control is not clickable.'}
$bounds=[regex]::Match($gear.GetAttribute('bounds'),'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
$x=[math]::Floor(([int]$bounds.Groups[1].Value+[int]$bounds.Groups[3].Value)/2)
$y=[math]::Floor(([int]$bounds.Groups[2].Value+[int]$bounds.Groups[4].Value)/2)
& $adb -s $id shell input tap $x $y
Start-Sleep -Seconds 2
& $adb -s $id shell uiautomator dump /sdcard/hikcentral-settings.xml | Out-Null
& $adb -s $id pull /sdcard/hikcentral-settings.xml (Join-Path $dir 'settings.xml') | Out-Null
$settingsXml=Get-Content (Join-Path $dir 'settings.xml') -Raw
if($settingsXml -notmatch 'Servidor \(DNS / IP\)'){
    & $adb -s $id shell input tap $x ($y+24)
    Start-Sleep -Seconds 2
    & $adb -s $id shell uiautomator dump /sdcard/hikcentral-settings.xml | Out-Null
    & $adb -s $id pull /sdcard/hikcentral-settings.xml (Join-Path $dir 'settings.xml') | Out-Null
    $settingsXml=Get-Content (Join-Path $dir 'settings.xml') -Raw
}
if($settingsXml -notmatch 'Servidor \(DNS / IP\)' -or $settingsXml -notmatch 'Porta HTTPS'){throw 'Settings dialog did not expose editable server and port fields.'}
[xml]$settingsDoc=$settingsXml
$editableFields=$settingsDoc.SelectNodes("//node[@class='android.widget.EditText' and @enabled='true' and @clickable='true']")
if($editableFields.Count -lt 4){throw 'Settings server, port, and credential fields were not exposed as editable inputs.'}
& $adb -s $id shell screencap -p /sdcard/hikcentral-settings.png
& $adb -s $id pull /sdcard/hikcentral-settings.png (Join-Path $dir 'settings.png') | Out-Null
& $adb -s $id shell input keyevent 4
Start-Sleep -Milliseconds 500
$register=$null
for($attempt=0;$attempt -lt 4 -and -not $register;$attempt++){
    & $adb -s $id shell uiautomator dump /sdcard/hikcentral-empty-form.xml | Out-Null
    & $adb -s $id pull /sdcard/hikcentral-empty-form.xml (Join-Path $dir 'empty-form.xml') | Out-Null
    [xml]$formDoc=Get-Content (Join-Path $dir 'empty-form.xml') -Raw
    $register=$formDoc.SelectSingleNode("//node[@class='android.widget.Button' and starts-with(@text,'CADASTRAR FUNC')]")
    if(-not $register){& $adb -s $id shell input swipe 360 1450 360 400 500; Start-Sleep -Milliseconds 500}
}
if(-not $register){throw 'Register button was not found for empty-form validation.'}
$registerBounds=[regex]::Match($register.GetAttribute('bounds'),'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
$rx=[math]::Floor(([int]$registerBounds.Groups[1].Value+[int]$registerBounds.Groups[3].Value)/2)
$ry=[math]::Floor(([int]$registerBounds.Groups[2].Value+[int]$registerBounds.Groups[4].Value)/2)
& $adb -s $id shell input tap $rx $ry
Start-Sleep -Milliseconds 500
& $adb -s $id shell uiautomator dump /sdcard/hikcentral-validation.xml | Out-Null
& $adb -s $id pull /sdcard/hikcentral-validation.xml (Join-Path $dir 'validation.xml') | Out-Null
$validation=Get-Content (Join-Path $dir 'validation.xml') -Raw
if($validation -notmatch 'Nome e sobrenome do colaborador' -or $validation -match 'Consultando organiza|Criando Person'){throw 'Empty form did not remain in local validation state.'}
& $adb -s $id shell screencap -p /sdcard/hikcentral-empty-validation.png
& $adb -s $id pull /sdcard/hikcentral-empty-validation.png (Join-Path $dir 'empty-validation.png') | Out-Null
Write-Host "Launch, settings fields, and empty-name validation passed. No settings changed and no record was submitted. Artifacts: $dir."
