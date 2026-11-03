[CmdletBinding(SupportsShouldProcess=$true,ConfirmImpact='High')]
param()
$ErrorActionPreference = 'Stop'
if ($PSCmdlet.ShouldProcess('VelaTVHotelPilot','Stop and remove Windows service')) {
    $service = Get-Service -Name VelaTVHotelPilot -ErrorAction SilentlyContinue
    if ($service) {
        if ($service.Status -ne 'Stopped') { Stop-Service -Name VelaTVHotelPilot }
        $python = 'C:\ProgramData\VelaTVHotel\app\.venv\Scripts\python.exe'
        $script = 'C:\ProgramData\VelaTVHotel\app\backend\windows_service.py'
        & $python $script remove
    }
    Write-Output 'Service removed. Pilot data, backups, config and application files were preserved.'
}
