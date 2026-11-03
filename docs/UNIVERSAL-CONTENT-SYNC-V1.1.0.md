# V1.1.0 Universal Content Sync Foundation (Phase 2A)

## Scope and baseline

This branch starts at accepted V1.0.6 commit `172ae7b53a1d2547264eab617935255429751763`. The approved `hotel_content.json`, Fiore24 assets, guest Home layout, Quick Apps, weather, enrollment and legacy media API are unchanged. The app remains `net.velalab.tvhotel`; V1.1.0 uses version code 15.

No live server, DNS, Cloudflare, production database or TV default Home setting is changed by this branch.

## Content package contract

An authenticated enrolled device requests `GET /api/v1/device/content-manifest` over HTTPS with its device bearer token. The response has `schemaVersion: 1`, a numeric-string `contentVersion` (the CMS published revision ID), UTC `publishedAt`, `minAppVersionCode: 15`, and one to 31 files. The first file is `content.json`; subsequent files are optional images or MP4 videos. Each file declares its relative package path, same-origin download path, SHA-256, byte size and, for media, duration. Maximum content size is 256 KiB, media file size 50 MiB and package size 200 MiB. The [manifest schema](schema/content-sync-manifest-v1.schema.json) and [hotel content schema](schema/hotel-content-v1.schema.json) are versioned with the client.

The client downloads `GET /api/v1/device/content/{contentVersion}/{sha256}.json` and existing `GET /api/v1/media/{sha256}.{extension}` with the same device token. Requests reject non-HTTPS endpoints, redirects, query strings, fragments, path traversal and cross-origin file URLs. The server serves only the *currently published* content revision and media in its published playlist; unpublished or revoked-device access fails. CMS Draft, Preview, Publish, Rollback and existing enrollment remain unchanged.

`schemaVersion` and `minAppVersionCode` are checked before any file download. Invalid JSON, hash or byte-size mismatch, incomplete transfer, unavailable storage and incompatible schema leave the active package untouched. Updating content requires a new APK only when the schema or minimum version exceeds the client capability.

## Android resolution order and activation

Home first renders the accepted bundled seed. A background worker resolves the source in this order: verified active package, verified previous package, validated V1.0.6 legacy cache, bundled seed. `Offline Only` skips all content sync requests; `Auto Sync` is the default to preserve V1.0.6 behavior. Staff Tools exposes the mode switch. An enrolled device with an HTTPS endpoint may sync automatically; an unconfigured device keeps its offline content.

Downloads go to a private temporary directory. The client streams each file, enforces byte limits, checks declared size and SHA-256, parses and validates content, copies all files into a staging directory, and verifies the staged package. It then rotates `active` to `previous` and promotes the stage. A failed promotion restores the old active package. A corrupt active package falls back to the verified previous generation. This is a single-generation rollback, not a history of all revisions. V1.0.6 legacy endpoint remains a fallback only when the new endpoint responds 404 and there is no active/previous new package.

The content sync mode is device-local and contains no secret. The endpoint remains HTTPS-only. Enrollment tokens stay in the Android Keystore-backed credential store. No admin password or token is bundled in the APK.

## Internal QA build and signing

Each push to a feature branch runs backend contract tests, Android compilation and unit tests, then assembles a debug QA APK with the 24 approved Fiore images. CI verifies source-image hashes and APK asset hashes, version and package metadata, and records the signing certificate digest in the artifact. It never deploys, creates a Release, or publishes to Vela App Store.

GitHub-hosted debug signing keys are ephemeral. Their APK may have a **different certificate from the previously installed TCL QA APK**. For an in-place update, build on the approved Windows QA machine with its existing `C:\Users\ADMIN\.android\debug.keystore`, compare the SHA-256 certificate fingerprint with the accepted V1.0.6 build, and use `-PincludeApprovedFioreMedia=true`. Never create a replacement keystore to force an update. The GitHub artifact remains useful for fresh internal installations and CI validation.

## Phase 2B boundary

Phase 2A does not configure a live hotel server or enroll a real TV. Phase 2B needs operator approval for endpoint installation, TLS and tunnel configuration, CMS publishing workflow, storage quota/retention policy, on-device 1080p/4K and TCL update tests, and operational monitoring. The client currently checks for updates on Home launch and when staff switches to Auto Sync; periodic scheduling can be added after the pilot refresh policy is agreed.
