# Windows 11 Hotel Server Pilot — installation package, not a deployment

This package continues the existing `backend/hotel_api.py` and V1.1.0 content sync contract. It is prepared for a **single pilot Windows 11 host**. No hotel computer, DNS, Cloudflare, production service, TV room, or Vela App Store has been changed by creating it. Android V1.1.0/code 15 and Fiore24 are untouched.

## Architecture and ports

```
TCL TV --TLS with public CA certificate--> hotel-tv.example.com:443
  reverse proxy (device/media/health paths only) --> 127.0.0.1:8131
Staff browser --TLS, allowed staff LAN/VPN only--> hotel-admin.example.com:443
  reverse proxy (admin UI/API) --> 127.0.0.1:8131
                    VelaTVHotelPilot Windows service (LocalService)
                    SQLite + media: C:\ProgramData\VelaTVHotel\data
```

The Python HTTP process binds **only** to `127.0.0.1`. The reverse proxy is the TLS boundary. In a hotel LAN, use a hostname controlled by the owner, a publicly trusted certificate obtained through DNS-01, and split-horizon DNS pointing the hostname to the Windows pilot server's private address. This lets an unmodified TCL/Android trust store validate the certificate and hostname. The example [Caddyfile](../ops/windows/Caddyfile.example) is a starting configuration; Caddy supports certificate/key files, path matchers, IP matchers and reverse proxy routing in its [official documentation](https://caddyserver.com/docs/caddyfile/directives/tls), [matchers](https://caddyserver.com/docs/caddyfile/matchers), and [reverse proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy). Replace the example admin CIDR with the real staff/VPN subnet and keep admin routing off the TV hostname. The certificate, DNS, firewall and Caddy service are **not** installed by these scripts.

## Configuration and service preparation

1. On the **designated pilot host**, prepare machine-wide Python 3.12, a separate backup directory with at least 2 GiB free, and at least 5 GiB free on C:. A per-user Python installation cannot serve LocalService. No Python packages need to be installed into the base interpreter. The installer will create `C:\ProgramData\VelaTVHotel\app\.venv` and install pinned Pillow 11.3.0 and pywin32 312 **only there**. pywin32 [cautions against running its post-install script in a venv](https://pypi.org/project/pywin32/); this package does not run that script.
2. Copy [pilot.env.example](../ops/windows/pilot.env.example) to `C:\ProgramData\VelaTVHotel\pilot.env`. Set `HOTEL_PROFILE=pilot`, the private data/approved seed paths and the TV HTTPS hostname. Generate two **different** random 32+ character editor/publisher tokens in a secure password manager; do not paste them into command history, the repository or screenshots. The installer rejects placeholders. Restrict the file to Administrators, SYSTEM and LocalService. No device token is stored in this file.
3. Prepare the TLS certificate and proxy routing separately. Do not configure Android cleartext access or disable certificate verification. Validate DNS and the certificate SAN/chain on a TCL before enrollment.
4. Run the [read-only preflight](../ops/windows/Preflight-Pilot.ps1) in an elevated PowerShell session with `-PilotHost <real-TV-hostname> -BackupDestination <existing-backup-directory> -PythonExe 'C:\Program Files\Python312\python.exe'`. It requires real A/AAAA records and verifies the TLS certificate chain and hostname through Windows trust. It checks active Firewall profiles, port 8131, service/data conflicts, disk space, a separate backup volume and private config ACLs. It never writes a test file, so **backup write permission still needs a separately approved write test**. Prefer an external or protected network backup location; a second partition on the same physical disk does not protect against disk failure. The real TV hostname, staff subnet, DNS and certificate must be ready before installation.
5. After separate installation approval and a passing preflight, run `Install-Pilot.ps1 -RepoRoot <checked-out-repository> -BackupDestination <existing-backup-directory> -PythonExe 'C:\Program Files\Python312\python.exe'`. It validates the private config before writing, copies only backend Python files and the accepted `hotel_content.json` seed, creates the dedicated venv, installs pinned dependencies inside it, sets private ACLs and registers delayed automatic `VelaTVHotelPilot` as LocalService. It refuses to overwrite an existing app directory, data directory or service. Pass `-StartService` only during the approved pilot installation window. No base Python packages are changed.
6. Start the approved HTTPS proxy and service, then run [Verify-Pilot.ps1](../ops/windows/Verify-Pilot.ps1). It checks `/readyz` on loopback and `/healthz` through normal TLS validation. Test that the TV hostname returns 404 for `/admin`, the staff hostname rejects a non-staff source, and the Windows firewall rejects remote TCP/8131.

The example config is intentionally unusable until populated. `pilot_runtime.py --config <private env path> --check` validates it without starting a server. `HOTEL_PROFILE=production` is rejected by the pilot launcher.

The service host is `app\.venv\Scripts\pythonservice.exe`, with its pywin32 and Python DLLs copied **inside the venv**, and a venv-local `.pth` pointing to the deployed backend. The service is registered through the venv interpreter. This follows the pywin32 [venv service workaround discussion](https://github.com/mhammond/pywin32/discussions/2734), which also notes that actual Service Control Manager startup was not proved by that example. Before accepting the hotel machine, inspect `Win32_Service.PathName` and `StartName`, start under LocalService, reboot once, and verify `/readyz` and logs. The machine-wide base Python remains a runtime dependency and must not be removed or upgraded without retesting the service. Backup, Restore and Uninstall scripts call the same venv interpreter.

## Content publishing and device workflow

The first Draft comes from the **approved bilingual offline seed**, including intentionally empty Explore Krabi and FAQ content. There is no Published version until a publisher explicitly acts. Editors can change Draft, Validate and Preview; publishers can also upload media, save a draft playlist, Publish, inspect history, Rollback, enroll TVs, assign rooms and revoke tokens. The [CMS](../backend/cms.html) shows separate sections for Hotel Information, Welcome, About, Accommodation, Dining, Spa & Pool, Guest Information, Contact and Explore Krabi. Fields already in the approved seed are shown; no missing promotions, prices, activities, FAQ or attractions are manufactured.

Publishing is a single SQLite `BEGIN IMMEDIATE` transaction. It validates content, playlist, each media SHA-256 and format, generates the V1 manifest, inserts a revision and switches the published pointer **together**. Draft save and media upload alone never affect TVs. Rollback atomically changes the pointer to an existing revision. The Android app reads the new version on its next Home sync and keeps its last known good package if validation or download fails. Staff should confirm the TV screen before broadening the pilot.

Device enrollment codes expire after 10 minutes and are single use. Device bearer tokens are stored only as hashes in SQLite; the Android side keeps its token in Keystore-backed storage. The CMS lists device ID, label, room, revocation state, last seen time, last completed content download and content version delivered. **A completed download is not proof that Android activated the package**; that acknowledgment is future work. Guests cannot assign room numbers from the TV. Publisher role controls room assignment and revocation.

## API endpoints

| Endpoint | Purpose | Authorization |
| --- | --- | --- |
| `GET /healthz`, `GET /readyz` | Liveness and local DB readiness | None; expose only healthz through TV proxy |
| `POST /api/v1/device/enroll` | Consume one-time code, issue device ID/token | Valid code |
| `GET /api/v1/device/content-manifest` | Published version, SHA-256/size/compatibility | Active device token |
| `GET /api/v1/device/content/{version}/{sha}.json` | Published content only | Active device token |
| `GET /api/v1/media/{sha}.{ext}` | Published media only | Active device token |
| `GET /admin`, `/admin/cms.css`, `/admin/cms.js` | Staff CMS | HTTP Basic over staff-only HTTPS |
| `GET /api/v1/admin/draft`, `/preview`, `/status`, `/revision/{id}` | Draft, preview, history and devices | Editor or publisher |
| `POST /api/v1/admin/validate`, `/draft` | Validate or save unpublished content | Editor or publisher |
| `POST /api/v1/admin/publish`, `/rollback`, `/media`, `/device`, `/enrollment` | Publish and device operations | Publisher |
| `PUT /api/v1/admin/media-file` | Bounded, hashed media upload | Publisher |

## Backup, restore and rollback

Use [Backup-Pilot.ps1](../ops/windows/Backup-Pilot.ps1) to create a new private ZIP. Python's SQLite online backup API captures a consistent database snapshot; the archive includes media bytes and a SHA-256 manifest. Keep multiple archives outside the server disk and restrict access because room assignments and hashed device tokens are present. Automated tests create and restore an archive and reject an overwrite.

[Restore-Pilot.ps1](../ops/windows/Restore-Pilot.ps1) requires a **new empty destination**. It checks paths, sizes, hashes and SQLite integrity before renaming staged data into place. It never overwrites the active data directory. For a server rollback, stop the pilot service, back up the current state, restore to a new directory, update the private `HOTEL_DATA_DIR` value, then restart and verify `/readyz`, the published Manifest and a pilot TV. To undo only a content change, use the CMS Version History and Rollback; do not replace the database. [Uninstall-Pilot.ps1](../ops/windows/Uninstall-Pilot.ps1) removes the Windows service with PowerShell confirmation and **preserves** data, backups, config and app files.

## Security and pilot limits

- The API process is loopback only; block remote TCP/8131 and permit TCP/443 only from approved LAN/VPN segments. The staff hostname must be inaccessible from guest/TV networks. HTTP Basic credentials are accepted only through this HTTPS staff path. Use a private browser session; close it after admin work.
- Server logs record method, path **without query**, and status; they do not record Authorization headers or request bodies. Session cookies are not issued. The private config contains admin tokens; device tokens are stored hashed in SQLite. Protect backup archives and TLS keys with ACLs.
- Uploads are capped at 50 MiB and checked by SHA-256 and decoded JPEG/PNG format/dimensions. MP4 requires `ffprobe` on the approved pilot host and accepts only H.264 at up to 1080p and 120 seconds. If `ffprobe` is absent, video upload is rejected. This does not alter bundled Fiore24 WebP photos.
- The server does not transcode media or schedule regular TV sync. Phase 2B does not install a proxy, issue certificates, migrate production data, confirm LocalService startup on the hotel's hardware, or test the real TCL over the hotel LAN. These remain explicit pilot-installation acceptance checks. No 79-room rollout is authorized.
