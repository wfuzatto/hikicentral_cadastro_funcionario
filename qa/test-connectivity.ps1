[CmdletBinding()]
param(
    [string]$HostName = '608905397dde.sn.mynetname.net',
    [int]$Port = 37443,
    [string]$Serial
)
$ErrorActionPreference = 'Continue'
if ($HostName -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$' -or $Port -lt 1 -or $Port -gt 65535) { throw 'Host or TCP port is invalid.' }
$reportDir = Join-Path $PSScriptRoot 'reports'
New-Item -ItemType Directory -Force $reportDir | Out-Null
$out = Join-Path $reportDir ('connectivity-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
function Record([string]$line) { $line | Tee-Object -FilePath $out -Append }

# Diagnose the Windows route.
$ips = @()
try {
    $ips = [Net.Dns]::GetHostAddresses($HostName)
    Record "Windows DNS: OK - $($ips -join ', ')"
} catch {
    Record "Windows DNS: FALHOU - $($_.Exception.GetBaseException().Message)"
}
$windowsTcp = $false
if ($ips.Count) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        if (-not $task.Wait(6000)) { throw 'timeout' }
        $windowsTcp = $client.Connected
        Record "Windows TCP: $(if ($windowsTcp) { 'OK' } else { 'FALHOU' }) - ${HostName}:$Port"
    } catch {
        Record "Windows TCP: FALHOU - $($_.Exception.GetBaseException().Message)"
    } finally { $client.Dispose() }
}
if ($windowsTcp) {
    try {
        $request = [Net.HttpWebRequest]::Create("https://${HostName}:$Port/")
        $request.Method = 'GET'
        $request.Timeout = 8000
        $request.AllowAutoRedirect = $false
        try { $response = $request.GetResponse() }
        catch [Net.WebException] {
            $response = $_.Exception.Response
            if (-not $response) { throw }
        }
        Record "Windows TLS/HTTP: OK - HTTP $([int]$response.StatusCode); this does not prove Artemis OpenAPI."
        $response.Dispose()
    } catch {
        Record "Windows TLS/HTTP: FALHOU - $($_.Exception.GetBaseException().Message)"
    }
} else { Record 'Windows TLS/HTTP: NÃO TESTADO porque TCP falhou.' }

# Diagnose the phone route independently when exactly one authorized device is present.
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $localAdb = Join-Path (Split-Path -Parent $PSScriptRoot) '..\android-platform-tools\platform-tools\adb.exe'
    if (Test-Path $localAdb) { $adb = (Resolve-Path $localAdb).Path }
}
if (-not $adb) {
    Record 'Android: BLOQUEADO; ADB não encontrado.'
} else {
    $devices = @(& $adb devices | Select-String '^\S+\s+device$')
    if ($Serial) { $devices = @($devices | Where-Object { $_.Line -match "^$([regex]::Escape($Serial))\s+device$" }) }
    if ($devices.Count -ne 1) {
        Record "Android: BLOQUEADO; encontrados $($devices.Count) dispositivos autorizados (exigido exatamente 1)."
    } else {
        $id = ($devices[0].Line -split '\s+')[0]
        $ping = (& $adb -s $id shell ping -c 1 -W 3 $HostName 2>&1 | Out-String)
        $match = [regex]::Match($ping, 'PING\s+\S+\s+\(([^)]+)\)')
        if ($match.Success) { Record "Android DNS: OK - $($match.Groups[1].Value)" }
        else { Record "Android DNS: FALHOU/INCONCLUSIVO - $($ping.Trim())" }
        if ($ping -match '1 received|1 packets transmitted, 1 received') { Record 'Android ICMP: OK' }
        else { Record 'Android ICMP: FALHOU/INCONCLUSIVO' }

        if ($id -notmatch '^[A-Za-z0-9._:-]+$') { throw 'ADB returned an invalid device serial.' }
        $tcpCommand = '"' + $adb + '" -s ' + $id + ' shell toybox nc -z -w 5 ' + $HostName + ' ' + $Port + ' 2>&1'
        $androidTcp = cmd.exe /d /c $tcpCommand | Out-String
        $tcpCode = $LASTEXITCODE
        if ($tcpCode -eq 0) { Record "Android TCP: OK - ${HostName}:$Port" }
        else {
            $detail = if ($androidTcp -match 'nc: connect: ([^\r\n]+)') { $Matches[1] } else { 'conexão recusada ou sem rota' }
            Record "Android TCP: FALHOU - $detail"
        }

        $tlsCommand = '"' + $adb + '" -s ' + $id + ' shell curl -I --connect-timeout 8 https://' + $HostName + ':' + $Port + '/ 2>&1'
        $tls = cmd.exe /d /c $tlsCommand | Out-String
        $tlsCode = $LASTEXITCODE
        if ($tlsCode -eq 0) { Record "Android TLS/HTTP: OK - $($tls.Trim())" }
        else {
            $detail = if ($tls -match 'curl: \(\d+\) ([^\r\n]+)') { $Matches[1] } else { 'falha antes da resposta TLS/HTTP' }
            Record "Android TLS/HTTP: FALHOU - $detail"
        }
    }
}
Write-Host "Network diagnosis complete. This does not authenticate to OpenAPI or prove the port belongs to Artemis. Report: $out"
