param([string]$PilotHost = 'hotel-tv.example.com')
$ErrorActionPreference = 'Stop'
$local = Invoke-RestMethod -Uri 'http://127.0.0.1:8131/readyz' -TimeoutSec 5
if ($local.status -ne 'ready') { throw 'Local API not ready' }
$public = Invoke-WebRequest -Uri "https://$PilotHost/healthz" -TimeoutSec 10
if ($public.StatusCode -ne 200) { throw 'HTTPS health check failed' }
Write-Output 'Loopback API and TLS-validated HTTPS endpoint responded. No certificate validation bypass was used.'
