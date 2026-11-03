param(
    [Parameter(Mandatory=$true)][string]$PilotHost,
    [Parameter(Mandatory=$true)][string]$BackupDestination,
    [string]$PythonExe = 'C:\Program Files\Python312\python.exe'
)
$ErrorActionPreference = 'Stop'
$failures = [System.Collections.Generic.List[string]]::new()
function Report([string]$Name, [bool]$Pass, [string]$Detail) {
    $state = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Output "$state $Name`: $Detail"
    if (-not $Pass) { $script:failures.Add($Name) }
}
function ValidHost([string]$Name) {
    if ($Name -match '(?i)(^|\.)(example|test|invalid|localhost)(\.|$)' -or
        $Name -match '(?i)^REPLACE_|^https?://' -or $Name.Length -gt 253) { return $false }
    if ($Name -notmatch '^(?=.{4,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])$' -or
        $Name -notmatch '\.' -or $Name -match '\.\.|\.-|-\.|^\d+\.\d+\.\d+\.\d+$') { return $false }
    return $true
}
Write-Output 'Read-only Vela TV Hotel pilot preflight'
Report 'hostname' (ValidHost $PilotHost) $PilotHost
$pythonReady = $false
if (Test-Path -LiteralPath $PythonExe -PathType Leaf) {
    $version = (& $PythonExe --version 2>&1 | Out-String).Trim()
    $pythonReady = $LASTEXITCODE -eq 0 -and $version -match '^Python 3\.12\.' -and
        $PythonExe -notmatch '(?i)\\Users\\|\\AppData\\'
} else { $version = 'missing' }
Report 'machine-wide Python 3.12' $pythonReady "$PythonExe ($version)"
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
Report 'elevated Administrator' $admin 'Required only for the later installation'
$existing = [bool](Get-Service -Name VelaTVHotelPilot -ErrorAction SilentlyContinue)
Report 'service absent' (-not $existing) 'VelaTVHotelPilot must not be overwritten'
$app = 'C:\ProgramData\VelaTVHotel\app'
$data = 'C:\ProgramData\VelaTVHotel\data'
Report 'app and data absent' (-not ((Test-Path -LiteralPath $app) -or (Test-Path -LiteralPath $data))) 'Existing files are never overwritten'
$config = 'C:\ProgramData\VelaTVHotel\pilot.env'
$configPresent = Test-Path -LiteralPath $config -PathType Leaf
Report 'private config present' $configPresent 'Contents are not printed'
if ($configPresent) {
    $allowed = @('S-1-5-18','S-1-5-32-544','S-1-5-19') # SYSTEM, Administrators, LocalService
    $aclSafe = $true
    try {
        foreach ($entry in (Get-Acl -LiteralPath $config).Access) {
            $sid = $entry.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
            if ($entry.AccessControlType -eq 'Allow' -and $sid -notin $allowed) { $aclSafe = $false }
        }
    } catch { $aclSafe = $false }
    Report 'private config ACL' $aclSafe 'Only SYSTEM, Administrators and LocalService may have allow entries'
}
$listener = @(Get-NetTCPConnection -LocalPort 8131 -State Listen -ErrorAction SilentlyContinue)
Report 'port 8131 free' ($listener.Count -eq 0) 'Loopback API port'
$active = @(Get-NetConnectionProfile -ErrorAction SilentlyContinue | Where-Object IPv4Connectivity -ne 'Disconnected')
$profiles = @(Get-NetFirewallProfile -ErrorAction SilentlyContinue)
$firewallReady = $active.Count -gt 0 -and $profiles.Count -gt 0
foreach ($network in $active) {
    $profile = $profiles | Where-Object Name -eq ([string]$network.NetworkCategory)
    if (-not $profile -or -not $profile.Enabled) { $firewallReady = $false }
}
Report 'active Firewall profiles enabled' $firewallReady 'A disabled profile is unsafe for pilot use'
$openApiRule = $false
foreach ($filter in @(Get-NetFirewallPortFilter -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq '8131' })) {
    $rule = $filter | Get-NetFirewallRule -ErrorAction SilentlyContinue
    if ($rule.Enabled -eq 'True' -and $rule.Direction -eq 'Inbound' -and $rule.Action -eq 'Allow') {
        $openApiRule = $true
    }
}
Report 'no inbound allow rule for 8131' (-not $openApiRule) 'API must remain loopback-only'
$backup = Test-Path -LiteralPath $BackupDestination -PathType Container
if ($backup) { try { $null = Get-Acl -LiteralPath $BackupDestination } catch { $backup = $false } }
Report 'backup destination exists and ACL readable' $backup $BackupDestination
$rootDisk = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'" -ErrorAction SilentlyContinue
Report 'system disk free >= 5 GiB' ($null -ne $rootDisk -and $rootDisk.FreeSpace -ge 5GB) 'C: stores the venv and service'
if ($backup) {
    $backupDrive = [System.IO.Path]::GetPathRoot((Resolve-Path -LiteralPath $BackupDestination).Path)
    $driveName = $backupDrive.TrimEnd('\')
    $backupDisk = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='$driveName'" -ErrorAction SilentlyContinue
    Report 'backup free >= 2 GiB' ($null -ne $backupDisk -and $backupDisk.FreeSpace -ge 2GB) $backupDrive
    Report 'backup on separate volume' ($driveName -ne 'C:') 'Use an external or protected network destination for durable backups'
}
if (ValidHost $PilotHost) {
    $addresses = @()
    foreach ($type in @('A','AAAA')) {
        try { $addresses += @(Resolve-DnsName -Name $PilotHost -Type $type -ErrorAction Stop |
            Where-Object { $_.IPAddress -and $_.Type -eq $type } | Select-Object -ExpandProperty IPAddress) }
        catch { }
    }
    Report 'DNS A/AAAA address' ($addresses.Count -gt 0) "$($addresses.Count) address record(s)"
    $tlsReady = $false
    if ($addresses.Count -gt 0) {
        $tcp = [System.Net.Sockets.TcpClient]::new()
        try {
            $connect = $tcp.BeginConnect($PilotHost, 443, $null, $null)
            if (-not $connect.AsyncWaitHandle.WaitOne(5000)) { throw 'connect timeout' }
            $tcp.EndConnect($connect)
            $ssl = [System.Net.Security.SslStream]::new($tcp.GetStream(), $false)
            try { $ssl.AuthenticateAsClient($PilotHost); $tlsReady = $ssl.IsAuthenticated }
            finally { $ssl.Dispose() }
        } catch { Write-Output "TLS validation failed: $($_.Exception.GetType().Name)" }
        finally { $tcp.Dispose() }
    }
    Report 'trusted TLS certificate for hostname' $tlsReady 'System trust and hostname validation; no bypass'
}
Write-Output 'No service, firewall, DNS, certificate or files were modified.'
if ($failures.Count -gt 0) { Write-Output "Preflight failed: $($failures -join ', ')"; exit 1 }
