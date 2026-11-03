# Phase 2C.4 — HTTPS development gateway preparation

Audit date: 2026-11-03. Starting HEAD: `5f3d4bb9ca393faf5c2f42d31d09f1f46e1a3264`. This package prepares a proposed gateway only. Nothing in this phase installs or starts Caddy, opens a port, changes DNS/firewall/TLS, issues a certificate, or changes another VelaLab service.

## Read-only infrastructure audit

| Item | Result | Evidence / consequence |
| --- | --- | --- |
| Development PC LAN | OBSERVED | Ethernet (Realtek, 1 Gbps) is Up. It has `192.168.15.131/24` and `192.168.0.131/24` on the same interface; default route uses `192.168.15.1`. Choose the correct TCL-facing address only after the TV network is known. Wi-Fi is disconnected; Tailscale is a separate private interface. |
| TCL to PC reachability | UNKNOWN | TCL IP, subnet, client isolation and route are not available from this PC audit. Neither same-subnet membership nor successful communication is proven. |
| Backend / ports | PASS for isolation | Development backend PID 24064 listens on `127.0.0.1:8132`; `/healthz` and `/readyz` return OK. No listener on TCP 443 or 8131. Other listeners exist on `0.0.0.0:8443` and `:11443` (Windows IP Helper process); do not change them. |
| Caddy | NOT PRESENT in current inspection | No `caddy` command or Caddy/Vela Hotel Windows service was found. Caddy syntax/runtime validation remains pending. |
| DNS provider | OBSERVED | `velalab.net` delegates to `rene.ns.cloudflare.com` and `stella.ns.cloudflare.com`. `hotel-tv-dev.velalab.net` returned NXDOMAIN for A and AAAA on the PC resolver. Public delegation does not prove this operator has DNS edit rights. |
| DNS management authorization | UNKNOWN — gate | Verify the owner's Cloudflare zone access and authority to create scoped `_acme-challenge` TXT records and the chosen LAN DNS mapping before certificate issuance. No DNS API token was inspected or recorded. |
| DNS-01 feasibility | CONDITIONAL | Cloudflare DNS-01 is technically possible with an approved ACME client and a narrowly scoped zone DNS-edit credential. Issuance still depends on ownership, CAA policy, outbound ACME/DNS access and propagation. No certificate was requested. |
| Firewall | FAIL for safe exposure | Domain, Private and Public Windows Firewall profiles are currently disabled. Ethernet network category is Public. A future allow rule alone would not restrict 443 until the active profile is enabled. Enabling it may affect existing VelaLab/Windows services and requires a separate impact review. |
| Other services | UNKNOWN impact | Ports 8443 and 11443 and existing services/rules must remain undisturbed. This package does not alter service startup, IIS, Tailscale or Cloudflare Tunnel. |

## Proposed gateway

`ops/windows/dev-gateway/Caddyfile.example` is separate from the pilot `ops/windows/Caddyfile.example`. It accepts a single development hostname and binds TCP 443 to one selected Ethernet IPv4 address; its only upstream is `127.0.0.1:8132`. It forwards `GET /healthz`, `POST /api/v1/device/enroll`, and GET requests for the content manifest, versioned content, legacy manifest and published media. `/admin`, `/admin/*`, `/api/v1/admin` and `/api/v1/admin/*` receive 403; all other paths/methods receive 404. Backend authentication still governs device endpoints. Request bodies are capped at 4 KB. Access logs rotate, remove all request headers and URL paths/queries, and do not include request bodies. The operator must keep the log directory private. The Caddy admin API is disabled.

The template uses an **existing certificate and private key**, not automatic issuance. `auto_https off` prevents Caddy from attempting certificate issuance or opening an HTTP redirect listener; explicit `tls` still serves HTTPS. The certificate must be publicly trusted by the TCL and contain the exact development hostname in its SAN. Do not use `tls internal`, a self-signed certificate, Android trust bypass, or cleartext. The Android ServerEndpoint accepts `https://<host>` on the default HTTPS port with no path or query. A private LAN A record may be supplied by approved split DNS while public DNS-01 TXT validation proves domain control; no public device endpoint is required. Avoid Cloudflare proxying of a private LAN address.

Cloudflare is inferred from public NS records only. The [Caddy TLS DNS documentation](https://caddyserver.com/docs/caddyfile/directives/tls) and [Cloudflare DNS module](https://github.com/caddy-dns/cloudflare/blob/master/README.md) describe one possible DNS-01 route after rights are verified. The committed template deliberately contains no DNS token or automatic certificate flow. Caddy `request_body max_size` requires version 2.10 or newer, per the [Caddy documentation](https://caddyserver.com/docs/caddyfile/directives/request_body). Its [logging filter](https://caddyserver.com/docs/caddyfile/directives/log) supports field deletion; validate the final Caddyfile with the actual binary before any launch.

## Gates before a separate installation approval

1. Obtain the TCL's IP, subnet and gateway, confirm its Wi-Fi/LAN isolation policy, and test route to the selected PC Ethernet IP. Do not infer the TCL is on either PC subnet.
2. Verify that an authorized DNS administrator controls `velalab.net`, agrees to the distinct development name, and can issue/renew a trusted DNS-01 certificate. Check CAA and ACME policy. Confirm a reliable certificate renewal and secure key storage procedure.
3. Select one PC Ethernet IPv4 address and a reserved/static address plan. Confirm no duplicate IP. Resolve the development hostname to that exact address from the TCL, with no conflicting public AAAA answer.
4. Audit existing inbound/outbound firewall dependencies, especially 8443/11443, before enabling the Public profile. Design a 443 allow rule scoped to the selected local IP and the TCL's actual IP or smallest test subnet. Confirm all other services retain access after the profile is enabled. Never rely on a 443 allow rule while the firewall is disabled.
5. Obtain a Caddy binary supporting `request_body` (2.10+) and confirm its provenance. Validate the Caddyfile, certificate path permissions, SAN, chain, expiry and log directory. Caddy must not bind `0.0.0.0`, Tailscale or 80.
6. Back up the existing network configuration and record a rollback operator/window. Perform physical TCL tests before any wider availability.

## Prepared commands for an approved future installation

These are **examples for a later approved maintenance window**, not commands executed in this phase. Replace angle-bracket values from verified inventory. Run configuration validation before starting a process; do not copy a token into the command line or repository.

```powershell
# Read-only verification first; use a Caddy 2.10+ executable obtained after approval.
$env:HOTEL_DEV_HOST = '<approved-dev-hostname>'
$env:HOTEL_DEV_BIND_IP = '<verified-TCL-facing-Ethernet-IPv4>'
$env:HOTEL_DEV_CERT_PEM = '<private-path-to-fullchain.pem>'
$env:HOTEL_DEV_KEY_PEM = '<private-path-to-key.pem>'
$env:HOTEL_DEV_ACCESS_LOG = '<private-path-to-access.log>'
& '<approved-caddy.exe>' validate --config '<workspace>\ops\windows\dev-gateway\Caddyfile.example' --adapter caddyfile
Get-NetTCPConnection -State Listen -LocalPort 443,8132 -ErrorAction SilentlyContinue
Resolve-DnsName $env:HOTEL_DEV_HOST -Type A
```

Only after the firewall impact review and separate approval: enable the applicable firewall profile with pre-existing service rules preserved, add an inbound TCP 443 allow rule scoped to the selected **local** IP and actual TCL remote IP, and run the validated Caddy configuration under a dedicated restricted account. The exact commands must be tailored to the reviewed machine state; do not run a blanket firewall reset or change IP/DNS automatically. Keep backend bound to loopback. A service installation is optional for development and is not part of this package.

```powershell
# Approved future window only. Confirm the Public profile can be enabled safely first.
$devRule = 'VelaTVHotelDevGateway-TCL-443'
$tclIp = '<verified-TCL-IPv4>'
New-NetFirewallRule -Name $devRule -DisplayName $devRule -Direction Inbound `
  -Action Allow -Protocol TCP -LocalAddress $env:HOTEL_DEV_BIND_IP `
  -RemoteAddress $tclIp -LocalPort 443 -Profile Public
# Enable the Public profile only under the separately reviewed firewall change plan.
# Run the validated Caddy binary with a dedicated restricted account.
& '<approved-caddy.exe>' run --config '<workspace>\ops\windows\dev-gateway\Caddyfile.example' --adapter caddyfile
```

### Rollback for that future change

1. Stop **only** the newly started development Caddy process/service; do not stop the backend or other VelaLab services.
2. Remove **only** the specifically named development 443 firewall rule created for this gateway, then restore the recorded prior firewall profile state if that change was part of the approved window. Preserve every pre-existing rule.
3. Restore the prior approved LAN DNS mapping if one was changed; do not delete unrelated zone records. Revoke only the dedicated DNS-01 credential if one was created for this gateway.
4. Verify port 443 is closed, 8132 remains loopback-only, and pre-existing 8443/11443 listeners/services still function. Check app fallback to last-known-good while the gateway is off.

```powershell
# Approved rollback window only. Stop the gateway's recorded process or service first.
Get-NetFirewallRule -Name 'VelaTVHotelDevGateway-TCL-443' -ErrorAction SilentlyContinue |
  Remove-NetFirewallRule
Get-NetTCPConnection -State Listen -LocalPort 443,8132,8443,11443 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

## Validation plan for TCL online QA

| Check | Expected result |
| --- | --- |
| TLS chain and hostname | TCL trusts the certificate chain; SAN matches the exact `https://` host entered in Staff Tools. No trust bypass. |
| Device routing | Health returns 200. Valid enrolled device receives manifest, content and authorized published media; anonymous/invalid device requests receive 401 or 403. |
| Admin rejection | `/admin`, `/admin/cms.js`, `/api/v1/admin/draft` and all non-allowlisted paths never reach backend through this host. |
| Isolation | `8132` is reachable only from PC loopback, not TCL/LAN; 443 is reachable only from the approved TCL scope. |
| DNS | TCL resolves the dev hostname to the selected private PC IP, with no stale A/AAAA mismatch. |
| Enrollment | Fresh one-time code works once, expired/reused code fails; token is not logged. |
| Content/media | Published version and file SHA-256 match the manifest; Home displays synchronized content without changing the APK. |
| Gateway failure | When the gateway is intentionally stopped, TCL retains last-known-good content and remains navigable; recovery resumes sync. |

No TCL network or physical test is claimed here. Current blockers: DNS management rights, TCL network details, trusted certificate, inactive firewall profiles, and absent Caddy binary. The V1.1.0 Android runtime and Fiore24 assets are unchanged.
