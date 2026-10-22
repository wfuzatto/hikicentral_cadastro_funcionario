[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$reports = Join-Path $PSScriptRoot 'reports'
New-Item -ItemType Directory -Force -Path $reports | Out-Null
$report = Join-Path $reports ("offline-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".log")
Start-Transcript -Path $report | Out-Null
try {
  Push-Location $root
  try {
    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if (-not $gradle) { throw 'Instale Gradle 8.10.2 (ou configure PATH) para o Android.' }
    & gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Gradle falhou.' }
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $maven) { throw 'Instale Maven 3.9+ (ou configure PATH) para o backend.' }
    Push-Location (Join-Path $root 'backend')
    try {
      & mvn -B --no-transfer-progress clean verify
      if ($LASTEXITCODE -ne 0) { throw 'Testes mock do backend falharam.' }
    } finally { Pop-Location }
    Write-Host 'QA OFFLINE APROVADO. Nenhuma conexão com HikCentral, ADB ou aplicativo instalado.'
  } finally { Pop-Location }
} catch {
  Write-Error $_
  exit 1
} finally { Stop-Transcript | Out-Null }
