param([Parameter(Mandatory=$true)][string]$Archive,
      [Parameter(Mandatory=$true)][string]$NewDataDirectory)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $NewDataDirectory) { throw 'Restore never overwrites an existing directory' }
$python = 'C:\Program Files\Python312\python.exe'
$script = 'C:\ProgramData\VelaTVHotel\app\backend\pilot_backup.py'
& $python $script restore $Archive $NewDataDirectory
if ($LASTEXITCODE -ne 0) { throw 'Pilot restore failed' }
Write-Output 'Restored to a new directory. Stop service and change HOTEL_DATA_DIR only after verification.'
