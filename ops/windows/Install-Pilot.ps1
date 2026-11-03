param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [Parameter(Mandatory=$true)][string]$BackupDestination,
    [string]$PythonExe = 'C:\Program Files\Python312\python.exe',
    [switch]$StartService
)
$ErrorActionPreference = 'Stop'
$root = 'C:\ProgramData\VelaTVHotel'
$app = Join-Path $root 'app'
$data = Join-Path $root 'data'
$config = Join-Path $root 'pilot.env'
$venv = Join-Path $app '.venv'
$venvPython = Join-Path $venv 'Scripts\python.exe'
$repo = (Resolve-Path -LiteralPath $RepoRoot).Path
$seed = Join-Path $repo 'android-client\src\main\assets\hotel_content.json'
$runtime = Join-Path $repo 'backend\pilot_runtime.py'
if (-not (Test-Path -LiteralPath $seed -PathType Leaf)) { throw 'Approved hotel seed missing' }
if (-not (Test-Path -LiteralPath $config -PathType Leaf)) { throw 'Create and ACL pilot.env before installation' }
$lines = Get-Content -LiteralPath $config
$hostLine = @($lines | Where-Object { $_ -match '^HOTEL_PUBLIC_MEDIA_HOST=' })
if ($hostLine.Count -ne 1) { throw 'Exactly one public hostname is required' }
$pilotHost = $hostLine[0].Substring('HOTEL_PUBLIC_MEDIA_HOST='.Length)
$checks = @{
    'HOTEL_PROFILE' = 'pilot'
    'HOTEL_DATA_DIR' = $data
    'HOTEL_INITIAL_CONTENT' = (Join-Path $app 'hotel_content.json')
    'HOTEL_BIND_PORT' = '8131'
}
foreach ($key in $checks.Keys) {
    $found = @($lines | Where-Object { $_ -match "^$key=" })
    if ($found.Count -ne 1 -or $found[0].Substring($key.Length + 1) -ne $checks[$key]) {
        throw "Pilot configuration must set $key to the dedicated pilot value"
    }
}
& $PythonExe $runtime --config $config --check --seed-override $seed
if ($LASTEXITCODE -ne 0) { throw 'Pilot configuration invalid' }
$preflight = Join-Path $repo 'ops\windows\Preflight-Pilot.ps1'
& $preflight -PythonExe $PythonExe -PilotHost $pilotHost -BackupDestination $BackupDestination
if ($LASTEXITCODE -ne 0) { throw 'Read-only preflight did not pass' }
$baseCheck = & $PythonExe -c 'import sys; print(int(sys.version_info[:2] == (3, 12) and sys.prefix == sys.base_prefix))'
if ($LASTEXITCODE -ne 0 -or $baseCheck.Trim() -ne '1') { throw 'Machine-wide base Python 3.12 required' }
if (Test-Path -LiteralPath $app) { throw 'App directory already exists; installation will not overwrite it' }
if (Test-Path -LiteralPath $data) { throw 'Data directory already exists; installation will not overwrite it' }
if (Get-Service -Name VelaTVHotelPilot -ErrorAction SilentlyContinue) { throw 'Service already exists' }

New-Item -ItemType Directory -Path $app,$data,(Join-Path $app 'backend') -ErrorAction Stop | Out-Null
Copy-Item -Path (Join-Path $repo 'backend\*.py') -Destination (Join-Path $app 'backend')
Copy-Item -Path (Join-Path $repo 'backend\cms.*') -Destination (Join-Path $app 'backend')
Copy-Item -LiteralPath (Join-Path $repo 'backend\requirements-windows.txt') -Destination (Join-Path $app 'backend\requirements-windows.txt')
Copy-Item -LiteralPath $seed -Destination (Join-Path $app 'hotel_content.json')
& $PythonExe -m venv $venv
if ($LASTEXITCODE -ne 0) { throw 'Virtual environment creation failed' }
& $venvPython -m pip install --requirement (Join-Path $app 'backend\requirements-windows.txt')
if ($LASTEXITCODE -ne 0) { throw 'Isolated dependency installation failed' }
& $venvPython (Join-Path $app 'backend\prepare_windows_service.py')
if ($LASTEXITCODE -ne 0) { throw 'pywin32 venv service host staging failed' }
& $venvPython (Join-Path $app 'backend\pilot_runtime.py') --config $config --check
if ($LASTEXITCODE -ne 0) { throw 'Staged pilot configuration invalid' }
& $venvPython -c 'import sys, win32serviceutil, servicemanager; assert sys.prefix != sys.base_prefix'
if ($LASTEXITCODE -ne 0) { throw 'pywin32 is not importable inside the dedicated venv' }

& icacls $root /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict pilot root ACL' }
& icacls $app /T /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict pilot app ACL' }
& icacls $data /T /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-19:(OI)(CI)M' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict pilot data ACL' }
& icacls $config /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' '*S-1-5-19:R' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict pilot configuration ACL' }

$serviceScript = Join-Path $app 'backend\windows_service.py'
& $venvPython $serviceScript --startup delayed --username 'NT AUTHORITY\LocalService' install
if ($LASTEXITCODE -ne 0) { throw 'Service registration failed; inspect before retrying' }
$service = Get-CimInstance Win32_Service -Filter "Name='VelaTVHotelPilot'"
$hostExe = Join-Path $venv 'Scripts\pythonservice.exe'
if (-not $service -or $service.StartName -ne 'NT AUTHORITY\LocalService' -or
    $service.PathName -notlike "*$hostExe*") { throw 'Registered service identity or venv host mismatch' }
if ($StartService) { Start-Service -Name VelaTVHotelPilot }
Write-Output 'Dedicated venv and delayed automatic LocalService registered. HTTPS proxy remains a separate gate.'
