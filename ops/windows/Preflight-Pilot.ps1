param(
    [string]$PythonExe = 'C:\Program Files\Python312\python.exe',
    [string]$PilotHost = 'hotel-tv.example.com'
)
$ErrorActionPreference = 'Stop'
Write-Output 'Read-only Vela TV Hotel pilot preflight'
Write-Output "Windows: $([Environment]::OSVersion.VersionString)"
Write-Output "Python exists: $(Test-Path -LiteralPath $PythonExe)"
if (Test-Path -LiteralPath $PythonExe) { & $PythonExe --version }
Write-Output "Port 8131 listening: $([bool](Get-NetTCPConnection -LocalPort 8131 -State Listen -ErrorAction SilentlyContinue))"
Write-Output "Port 443 listening: $([bool](Get-NetTCPConnection -LocalPort 443 -State Listen -ErrorAction SilentlyContinue))"
Write-Output "Existing service: $([bool](Get-Service -Name VelaTVHotelPilot -ErrorAction SilentlyContinue))"
Write-Output "Config exists: $(Test-Path -LiteralPath 'C:\ProgramData\VelaTVHotel\pilot.env')"
Write-Output "Data exists: $(Test-Path -LiteralPath 'C:\ProgramData\VelaTVHotel\data')"
try { Resolve-DnsName -Name $PilotHost -ErrorAction Stop | Select-Object Name,IPAddress } catch { Write-Output 'Pilot DNS unresolved' }
Write-Output 'No service, firewall, DNS or files were modified.'
