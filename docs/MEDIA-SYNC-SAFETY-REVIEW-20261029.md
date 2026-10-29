# Media Sync / Offline Cache Safety Review — 2026-10-29

Scope: Vela TV Hotel pilot only; no production media endpoint, guest-room rollout, HOME intent, or personal Vela TV changes.

## Confirmed implemented safeguards
- HTTPS endpoint normalization; download URLs must use HTTPS on one explicitly supplied host, port 443, without redirects or credentials.
- Bounded manifest (1 MiB); files are streamed into an app-private directory with total 200 MiB limit.
- Each downloaded file is SHA-256 verified before the import; importer independently re-hashes all files.
- Staging->active swap preserves previous content on ordinary download/validation failures.
- Blank manifest is now rejected in downloader and cache; unauthorized manifest responses are REJECTED without modifying cached content.
- Server prototype remains loopback-only, with bearer-token check; it is not a room-ready enrollment service.

## Not yet proven / release blockers
- Android media sync is not scheduled from UI or service; secure per-device enrollment and Keystore-backed token storage are not implemented.
- No authenticated admin portal or approved two-phase server migration UI exists yet.
- Startup recovery checks for the manifest file, but not every asset's digest. Rehash off the UI thread, test corrupted active cache and fallback to previous.
- Activation uses multiple filesystem renames rather than a single atomic transaction; power-loss / disk-full recovery has not been fault-injection tested.
- Bitmap decode and hardware video codecs have not been QA-tested on a hotel TV; media playback can still be affected by malformed or oversized images.
- Content not yet validated by physical device D-pad/OK/Back, offline disconnect, reboot during sync, and low-storage tests.
- The resort's original logo is not present in the repository. Do not substitute a recreated logo.

## Pilot acceptance gates
1. Verify Android and Foundation Actions success on this feature branch, and inspect debug APK artifact.
2. Use the original resort logo asset only after upload and asset review.
3. Test on one non-production hotel TV without changing its default HOME app.
4. Validate live HTTPS device enrollment and manifest contract on staging before automatic sync is ever enabled.
5. Exercise: successful download, hash mismatch, HTTP 401/403, empty playlist, offline start, interrupted download, cache corruption, no free space, app switching, video decode.
6. Require explicit authorization before production deployment or changing TV launcher defaults.
