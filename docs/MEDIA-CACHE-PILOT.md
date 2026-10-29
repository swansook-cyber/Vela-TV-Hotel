# Verified Offline Media Cache (pilot)

The Android client now has a **local importer**, not a network downloader. It accepts a JSON manifest and media already present in an app-private source directory. It validates identifiers, extensions, duration and SHA-256 for each file, enforces a 200 MiB aggregate limit, stages a complete new set, and retains a previous set for rollback.

Current limitations / release blockers:
- No HTTPS download, no device enrollment, no secure per-device token.
- The current media contract must be extended with an explicit `extension` field (jpg/jpeg/png/mp4) before server/client integration.
- Filesystem rename-based activation is not crash-atomic across both renames. Add recovery of `previous` on startup, disk-space checks, durable manifest fsync and fault-injection tests before production.
- `activeSlides()` does not rehash files on every read. Files must only enter active through a successful verified import; threat model review pending.
- Playback currently points at a legacy `active-media` directory; integration with VerifiedMediaCache must be completed and tested.
- Media decode size, codec validation, symlink protection and storage cleanup still need hardening.

Never deploy this prototype as a guest-room launcher. No changes to Home Hub or existing personal Vela TV.
