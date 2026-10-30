# Vela TV Hotel V1.0.2 — Home Hub staging package

Scope: this is a **prepared package**, not a Home Hub installation. Nothing in this branch changes Cloudflare, DNS, live services, or the TV's default HOME. Source baseline is `feature/hotel-v1-rea-ui-rebuild-20261030` at `7ecb8b53aaedf2210078f6aeb6ade3cf7bce8efc`. Install only after owner approval, a read-only Home Hub preflight, and review of the existing tunnel.

## Runtime and isolation

- Debian 12 Bookworm; Debian's security-maintained `python3.11`. `deploy/homehub-staging/requirements.txt` has zero PyPI dependencies: the API uses the Python standard library. Pinning a Python patch number would block Debian security fixes.
- Dedicated system user `vela-hotel-stg`, no login shell; service `vela-tv-hotel-staging.service` is restricted with systemd filesystem, capability, device and privilege controls.
- API listens on `127.0.0.1:8131` only. The tunnel terminates public HTTPS at `hotel.velalab.net`; never expose port 8131 on a public interface.
- Private state `/var/lib/vela-tv-hotel-staging`: SQLite database `hotel.sqlite3` and `media/`. Code releases live in `/opt/vela-tv-hotel-staging/releases/<commit>`, selected by `current` symlink. Environment secrets live outside Git in `/etc/vela-tv-hotel-staging.env`.
- Editor and publisher tokens must be distinct random secrets of at least 32 characters. The example values intentionally fail startup validation. Never place real room/guest data in the repository.

## Before any installation (read-only)

1. On Home Hub, review the existing service inventory, tunnel config, hostname ownership, and storage capacity. Run `deploy/homehub-staging/preflight.sh` from a clean checkout. It checks Debian version, Python, required tools, port 8131, existing paths, and free space without changing them. Any `EXISTS` line needs human review; installation refuses to overwrite an existing staging service.
2. Confirm the proposed hostname is available and that publishing it will not reroute any VelaLab service. Review the tunnel guide below. Do not alter the current tunnel as part of this package preparation.
3. Confirm an off-host encrypted backup location and the owner-approved maintenance window. Ensure Home Hub firewall does not expose 8131.

## Installation procedure (future, requires owner approval)

1. Checkout this staging branch at the reviewed commit on Home Hub. Run the preflight and review its output.
2. Create `/etc/vela-tv-hotel-staging.env` from `deploy/homehub-staging/environment.example` with fresh independent secrets generated locally; owner `root`, group `vela-hotel-stg`, mode `0640`. Create the dedicated system group/user before assigning group ownership. Set `HOTEL_PUBLIC_MEDIA_HOST=hotel.velalab.net` and `HOTEL_DATA_DIR=/var/lib/vela-tv-hotel-staging`.
3. Run `sudo ./deploy/homehub-staging/install.sh --confirm-install` from the clean branch checkout. It refuses an existing service and starts only `vela-tv-hotel-staging.service`. It does not configure Cloudflare. Run `./deploy/homehub-staging/verify.sh`.
4. Check `systemctl status vela-tv-hotel-staging.service` and `journalctl -u vela-tv-hotel-staging.service`. Verify no secrets appear in logs. Perform public HTTPS/API checks after the tunnel is approved and configured.

## Cloudflare Tunnel proposal (no changes made)

Use `deploy/homehub-staging/cloudflared-ingress.example.yml` as a **merge guide**, not a replacement for the Home Hub tunnel config. Add a specific `hotel.velalab.net -> http://127.0.0.1:8131` ingress rule ahead of the existing catch-all. Keep the existing tunnel ID, credentials, routes, and service rules intact. Validate the full proposed config with `cloudflared tunnel ingress validate` and `cloudflared tunnel ingress rule https://hotel.velalab.net/healthz` before an approved reload. Add the DNS hostname route only with explicit approval. The hostname must present a trusted HTTPS certificate to Android; the TV app rejects HTTP, non-443 ports, query/fragment endpoints and hosts without valid TLS.

Protect `/admin` and `/api/v1/admin/*` using a Cloudflare Access self-hosted application with an owner-approved administrator identity policy; the CMS additionally requires its editor/publisher token. Keep `/api/v1/device/enroll`, `/api/v1/device/manifest`, and `/api/v1/media/*` accessible to the TV without interactive Access login, because those endpoints use one-time codes or device bearer tokens. Keep `/healthz` restricted to the required operations visibility. Cloudflare Tunnel by itself does not enforce CMS administrator identity. Test Access path matching and the Android flow before any guest rollout.

## CMS-to-TV integration

1. Browse `https://hotel.velalab.net/admin` after the tunnel and Access are approved. Sign in as `editor` or `publisher` with the corresponding token. The browser uses HTTP Basic over HTTPS.
2. Edit the sample content, which is explicitly marked TEST DATA, and Save Draft. Preview does not publish. Publisher uploads JPG/PNG/MP4 media with SHA-256 and MIME signature checks, adds playlist entries using `https://hotel.velalab.net/api/v1/media/<sha256>.<extension>`, and publishes a revision.
3. Publisher creates a 10-minute one-time enrollment code. On a pilot TV, set the server endpoint to `https://hotel.velalab.net` and enter the code. Android receives its own token, stores it with Android Keystore, fetches the manifest, and downloads only published allowlisted media with SHA-256 verification. Revoke the device in CMS to invalidate its token.
4. The approved V1.0.2 Android code uses `POST /api/v1/device/enroll`, `GET /api/v1/device/manifest`, and authenticated media URLs exactly as the backend does. **No APK rebuild is required for this API contract**, provided its current APK contains the verified V1.0.2 code, the hostname has trusted HTTPS, and the user configures that endpoint. This is source-level compatibility; real Home Hub and physical TV behavior remain unverified.

## Backup, restore and rollback

Before a staging upgrade or content recovery, run `python3.11 deploy/homehub-staging/backup_restore.py backup /var/lib/vela-tv-hotel-staging /secure/off-host/path/hotel-<timestamp>.tar.gz`. The script uses SQLite's online backup API, copies media, writes per-file SHA-256 hashes, and refuses an existing archive. Transfer the resulting mode-0600 archive to approved encrypted off-host storage; it contains potentially sensitive hotel data and device token hashes.

To restore, stop **only** `vela-tv-hotel-staging.service`, move the current data directory to a timestamped quarantine path, then run `python3.11 deploy/homehub-staging/backup_restore.py restore <archive> /var/lib/vela-tv-hotel-staging`. Restore requires a nonexistent destination, rejects unsafe archive entries, verifies hashes and SQLite integrity, and never deletes the previous directory. Set ownership to `vela-hotel-stg:vela-hotel-stg`, start the staging service, run `verify.sh`, and inspect CMS state. Preserve the quarantine directory until the owner approves disposal.

For a bad content revision, use the publisher's CMS Rollback action with a known good revision ID; it creates a new published revision. For a bad code release, after a backup run `sudo ./deploy/homehub-staging/rollback_release.sh --confirm-rollback <previous-commit-sha>`. The script switches only the staging code symlink and restarts only the staging service; it automatically restores the prior symlink if restart fails. It does not roll back the database. Never run this against a release with an incompatible schema without first restoring the reviewed database backup.

## Local validation versus Home Hub acceptance

Local automated tests cover draft, preview, publish, enrollment expiration and concurrent single use, manifest, media SHA-256, offline outage, content rollback, revocation, role enforcement, HTTPS manifest contract, and backup/restore integrity. `bash -n` validates scripts. CI runs the backend tests. No test here proves Cloudflare routing, Debian systemd hardening, physical TV operation, or the live Home Hub environment. Those require a separately approved installation and pilot test.
