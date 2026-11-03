param([Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference = 'Stop'
$python = 'C:\Program Files\Python312\python.exe'
$script = 'C:\ProgramData\VelaTVHotel\app\backend\pilot_backup.py'
& $python $script backup 'C:\ProgramData\VelaTVHotel\data' $Archive
if ($LASTEXITCODE -ne 0) { throw 'Pilot backup failed' }
