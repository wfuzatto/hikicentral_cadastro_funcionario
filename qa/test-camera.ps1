[CmdletBinding()]
param()
Write-Host 'Camera validation requires an authorized physical device and a consented operator. Run run-all.ps1 to open the app, then verify capture, preview, return navigation, and temporary-file cleanup manually. No real photo is captured by this script.'
exit 2
