[CmdletBinding()]
param([string]$HostName='608905397dde.sn.mynetname.net',[int]$Port=37443)
$ErrorActionPreference = 'Stop'
$out = Join-Path $PSScriptRoot ('reports\connectivity-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
try { $ips = [Net.Dns]::GetHostAddresses($HostName); "DNS: OK - $($ips -join ', ')" | Tee-Object -FilePath $out -Append }
catch { "DNS: FALHOU - $($_.Exception.Message)" | Tee-Object -FilePath $out -Append; exit 2 }
$tcp = [Net.Sockets.TcpClient]::new()
try { $tcp.Connect($HostName,$Port); "TCP: OK - $HostName`:$Port" | Tee-Object -FilePath $out -Append }
catch { "TCP: FALHOU - $($_.Exception.GetBaseException().Message)" | Tee-Object -FilePath $out -Append; exit 3 }
finally { $tcp.Dispose() }
try {
    $request = [Net.HttpWebRequest]::Create("https://${HostName}:$Port/")
    $request.Method = 'GET'; $request.Timeout = 8000; $request.AllowAutoRedirect = $false
    try { $response = $request.GetResponse() } catch [Net.WebException] { $response = $_.Exception.Response; if (-not $response) { throw } }
    "TLS/HTTP: OK - HTTP $([int]$response.StatusCode); isso não prova que o serviço é OpenAPI Artemis." | Tee-Object -FilePath $out -Append
    $response.Dispose()
} catch { "TLS/HTTP: FALHOU - $($_.Exception.GetBaseException().Message)" | Tee-Object -FilePath $out -Append; exit 4 }
Write-Host "Windows host diagnostic only; it does not verify Android network path or OpenAPI auth. Report: $out"
