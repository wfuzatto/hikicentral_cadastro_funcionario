[CmdletBinding()]
param()
Write-Host 'No standalone API probe is implemented yet. The app settings screen performs read-only root-organization and access-group queries using AK/SK stored in Android Keystore. Provide authorized credentials on-device; this suite never reads or logs secrets and never creates a Person.'
exit 2
