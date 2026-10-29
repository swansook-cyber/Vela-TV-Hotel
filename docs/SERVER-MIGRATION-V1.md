# Portable Server Configuration — V1

Vela Home Hub is the initial host, **not a hardcoded dependency**. The TV app can migrate to a hotel-hosted server, VPS, or cloud endpoint without rebuilding the APK.

## Configuration
- Admin-only settings: HTTPS API base URL, device enrollment status, server identity and last successful sync.
- Server URL must be validated as HTTPS with a public DNS hostname (or an explicitly enrolled private hostname via an admin-approved LAN mode). Reject credentials, query, fragment, cleartext and redirects to unapproved hosts.
- Device credentials must be stored with Android Keystore-backed encrypted storage, never in a resource, GitHub, logs, or the URL.
- A server change is a **two-phase operation**: validate endpoint and TLS, authenticate/enroll with the new server, fetch and validate a complete manifest, download and SHA-256 verify all required assets, then atomically activate the new configuration.
- On failure, keep the previous server and last known-good offline playlist. No empty or broken guest Home screen.
- The new server must implement the same versioned /api/v1 contract; backend storage and hosting technology are irrelevant to the TV app.
- Support manual rollback from Admin settings, and retain old cache until new content is verified.
- Never silently copy credentials or guest/device data between different server operators.

## API compatibility
GET /healthz (readiness only)
GET /api/v1/device/manifest (authenticated, versioned schema)
Media file download via allowlisted HTTPS host with content-length cap and digest verification.

## Tests required
URL validation; unreachable new server; expired TLS; unauthorized enrollment; interrupted download; digest mismatch; server version mismatch; rollback; network outage during startup; old content still playable.

## Deployment policy
No actual Home Hub, hotel network, Cloudflare, or production changes are authorized by this document.
