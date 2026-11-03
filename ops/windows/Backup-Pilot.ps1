param([Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference = 'Stop'
$python = 'C:\ProgramData\VelaTVHotel\app\.venv\Scripts\python.exe'
$script = 'C:\ProgramData\VelaTVHotel\app\backend\pilot_backup.py'
& $python $script backup 'C:\ProgramData\VelaTVHotel\data' $Archive
if ($LASTEXITCODE -ne 0) { throw 'Pilot backup failed' }
