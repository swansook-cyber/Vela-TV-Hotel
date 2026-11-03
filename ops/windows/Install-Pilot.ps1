param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [string]$PythonExe = 'C:\Program Files\Python312\python.exe',
    [switch]$StartService
)
$ErrorActionPreference = 'Stop'
$root = 'C:\ProgramData\VelaTVHotel'
$app = Join-Path $root 'app'
$config = Join-Path $root 'pilot.env'
if (-not (Test-Path -LiteralPath $PythonExe)) { throw 'Machine-wide Python 3.12 is required' }
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run as Administrator' }
if (-not (Test-Path -LiteralPath $config)) { throw 'Create and ACL pilot.env before installation' }
if (Test-Path -LiteralPath $app) { throw 'App directory already exists; installation will not overwrite it' }
if (Get-Service -Name VelaTVHotelPilot -ErrorAction SilentlyContinue) { throw 'Service already exists' }
$repo = (Resolve-Path -LiteralPath $RepoRoot).Path
$seed = Join-Path $repo 'android-client\src\main\assets\hotel_content.json'
if (-not (Test-Path -LiteralPath $seed)) { throw 'Approved hotel seed missing' }
$content = Get-Content -LiteralPath $config -Raw
if ($content -match 'REPLACE_WITH_|hotel-tv\.example\.com') { throw 'Pilot configuration contains placeholders' }
New-Item -ItemType Directory -Path $app,(Join-Path $root 'data') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $app 'backend') | Out-Null
Copy-Item -Path (Join-Path $repo 'backend\*.py') -Destination (Join-Path $app 'backend')
Copy-Item -Path (Join-Path $repo 'backend\cms.*') -Destination (Join-Path $app 'backend')
Copy-Item -LiteralPath (Join-Path $repo 'backend\requirements-windows.txt') -Destination (Join-Path $app 'backend\requirements-windows.txt')
Copy-Item -LiteralPath $seed -Destination (Join-Path $app 'hotel_content.json')
& icacls $root /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
& icacls $app /T /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
& icacls (Join-Path $root 'data') /T /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' | Out-Null
& icacls $config /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' '*S-1-5-19:R' | Out-Null
& $PythonExe -m pip install --requirement (Join-Path $app 'backend\requirements-windows.txt')
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed' }
& $PythonExe -m pywin32_postinstall -install
if ($LASTEXITCODE -ne 0) { throw 'pywin32 service support installation failed' }
& $PythonExe (Join-Path $app 'backend\pilot_runtime.py') --config $config --check
if ($LASTEXITCODE -ne 0) { throw 'Pilot config invalid; service not installed' }
& $PythonExe (Join-Path $app 'backend\windows_service.py') --startup auto install
& sc.exe config VelaTVHotelPilot obj= 'NT AUTHORITY\LocalService' start= delayed-auto | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not set restricted service identity' }
if ($StartService) { Start-Service -Name VelaTVHotelPilot }
Write-Output 'Pilot files staged and automatic service registered. HTTPS proxy and certificate remain separate approval gates.'
