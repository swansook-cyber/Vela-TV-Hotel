# Server Settings pilot implementation

Added:
- ServerEndpoint HTTPS URL syntax validation
- ServerConfigStore to persist **only** the active non-secret URL in app-private preferences
- ManifestClient probe of /healthz, no redirect following

Not yet implemented:
- Admin-only settings screen and PIN access
- Secure per-device credentials / Android Keystore
- Device enrollment, manifest fetch, media download, digest verification and atomic migration
- DNS/IP allowlist checks, redirects and certificate policy review
- Automatic fallback to last known-good server

**Do not** use probe success alone as authorization to switch servers. The probe only confirms an HTTP 200 response from the candidate endpoint. Never store tokens in SharedPreferences, URLs or source code.

No Home Hub deployment or TV fleet rollout is included.
