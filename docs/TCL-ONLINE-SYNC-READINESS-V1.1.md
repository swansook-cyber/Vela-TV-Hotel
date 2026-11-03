# TCL online sync readiness — Phase 2C.3

Audit date: 2026-11-03. Starting branch: `feature/hotel-v1-1-0-windows-pilot-server-20261103`; starting commit: `c6177637ba0d9e4faa2f18475f5f370853293baa`. This is a source and local-development audit, not a physical TCL online test.

## Android and backend audit

| Requirement | Result | Evidence and limit |
| --- | --- | --- |
| Staff can choose Offline Only or Auto Sync | PASS (code) | `MainActivity.showStaffTools`, `ContentSyncSettings.mode/setMode`; staff tools are opened by long OK on Guest Information. Physical remote operation remains to be checked. |
| Staff can set HTTPS server URL | PASS (code) | `MainActivity.showSetup`, `ServerEndpoint.normalizedHttpsUrl`, `ServerConfigStore.saveVerifiedEndpoint`; rejects HTTP, nonstandard ports and URL paths. |
| One-time code enrollment | PASS (code) | `MainActivity.enroll` posts to `/api/v1/device/enroll`; `backend/hotel_api.py` handles the code. One-time expiration and reuse are covered by backend tests. |
| Device token protected on TV | PASS (code) | `DeviceCredentials` encrypts the token with an Android Keystore AES-GCM key. Server URL is stored separately. |
| Authenticated manifest, content and media | PASS (code) | `HttpsContentFetcher.fetch` adds the bearer device token; `UniversalContentResolver.resolve` obtains manifest and files; `backend/hotel_api.py` authenticates device routes. |
| SHA-256 and APK/content compatibility | PASS (code) | `ContentSyncContract.parseManifest` checks schema, minimum app code, sizes, paths and SHA-256; resolver checks downloaded bytes before activation. |
| Atomic activation, rollback and offline cache | PASS (code) | `ContentPackageStore.activate/current` uses staging, active and previous packages; resolver retains saved content on failed downloads. |
| Home actually uses synchronized content | PASS (code) | `MainActivity.syncInBackground` assigns `content = selected.content` and redraws Home; screen content readers use `content`. On startup Home first displays the bundled/legacy seed, then the saved package after background resolution. A brief seed frame is possible. |
| D-pad, OK, Back, launcher navigation | PASS (code); PHYSICAL QA PENDING | Native focusable controls and `MainActivity.onBackPressed`; manifest retains HOME and LEANBACK_LAUNCHER. Verify on TCL with actual remote and return from external apps. |
| Live TCL HTTPS sync | MISSING (environment) | No reachable approved device-only HTTPS endpoint, DNS record or TCL trust verification exists in this development setup. No production service or network settings were changed. |

The accepted Android application remains V1.1.0, versionCode 15, package `net.velalab.tvhotel`. This change adds unit tests and documentation only. It does not change application code, approved Fiore24 media, offline seed or UI; no new APK or versionCode is needed for this audit.

## Local network observations and proposed pilot connection

On the development PC, the backend listens only on `127.0.0.1:8132`; port 443 has no listener, and `hotel.velalab.net` did not return an A record in this audit. The repository's `ops/windows/Caddyfile.example` uses placeholder hosts and port 8131 for a future pilot installation. It is not an active configuration. The development proxy would need to target loopback port 8132.

After separate installation approval, use a hotel-LAN-only reverse proxy on 443 with a hostname in the certificate SAN that exactly matches the URL entered on the TV. Obtain a publicly trusted certificate using DNS-01 or another method that does not require exposing the CMS; arrange LAN DNS resolution of that same hostname to the proxy. Restrict the TV virtual host to `/healthz`, `/api/v1/device/*` and `/api/v1/media/*`. Keep admin/CMS routes on a separate staff-only hostname and network policy. Keep backend port 8132 bound to loopback. Confirm the TCL trusts the full certificate chain and that Android default TLS validation succeeds. Do not enable cleartext, disable certificate checks, expose 8132, or change DNS/firewall/TLS as part of this phase.

Before any network change, validate the exact hostname, certificate SAN/expiry/chain, local DNS response, LAN address, staff access boundary and a backup/rollback plan. The current `Caddyfile.example` is a template, not ready for use as-is.

## Automated verification coverage

| Scenario | Test evidence |
| --- | --- |
| Enrollment and one-time code behavior | Backend API tests in `backend/test_hotel_api.py` and `backend/test_pilot_phase2b.py` |
| Online manifest and content download | `UniversalContentResolverTest.validManifestPassesCompatibilityAndDigestValidation`, `completeContentUpdateActivatesAtomically`; backend content sync contract tests |
| Media integrity | `contentAndMediaActivateAsOneVerifiedPackage`, `failedMediaIntegrityUpdateKeepsPreviousVersion` |
| Content activation and rollback | `completeContentUpdateActivatesAtomically`, `failedPromotionRestoresPreviousActivePackage` |
| Offline fallback | `firstInstallOfflineUsesBundledBaseline`, `offlineOnlyNeverCallsFetcher`, `serverTimeoutUsesLastKnownContent` |
| Invalid/revoked token | `revokedOrInvalidTokenKeepsActiveContent`; backend revoked-token tests |
| Unreachable server | `unreachableServerAfterRestartKeepsLastKnownGood` |
| Failed update | `sha256MismatchKeepsBundledBaseline`, `interruptedDownloadLeavesNoPartialActivation`, `failedMediaIntegrityUpdateKeepsPreviousVersion` |
| Restart with last-known-good | `newProcessReadsLastKnownGoodPackage`, `unreachableServerAfterRestartKeepsLastKnownGood` |
| HTTPS URL validation | `serverUrlRequiresHttpsDefaultPortAndRootPath` |

The resolver unit tests use a fake fetcher. They verify sync behavior and failure handling, but cannot prove TLS trust, TV Keystore operation, real enrollment UI or remote navigation. Those require physical TCL QA.

## Physical TCL online QA checklist

1. Record the installed APK package, versionCode and signing certificate SHA-256. Compare the certificate with any intended replacement APK before attempting an in-place update. The GitHub Actions debug certificate is not assumed to match the Windows QA certificate already on the TV.
2. Confirm the device-only HTTPS hostname resolves on the TCL LAN, exactly matches the certificate SAN, presents a valid chain and responds through the proxy while port 8132 remains inaccessible from LAN.
3. With Auto Sync selected in staff tools, enter the approved HTTPS URL and a fresh one-time code. Confirm enrollment succeeds, reuse/expiry fails, and the room assignment is administered server-side.
4. Publish a clearly identifiable test content version in the development CMS. Confirm manifest, content and media downloads, hashes, version and Home text/media update without an APK rebuild.
5. Restart the app and TV. Confirm the latest valid content remains. Disconnect the network; confirm the same saved guide remains and the UI stays usable. Switch to Offline Only and confirm no device sync requests occur.
6. Test invalid/revoked token, interrupted transfer, corrupted media, incompatible content and unreachable server. Confirm no partial content appears and the prior package survives.
7. With the TCL remote, test D-pad, OK, Back, long OK to staff tools, all content pages, Quick Apps and return from an external app. Confirm the HOME choice remains under user control.
8. Verify guest access cannot reach CMS/admin routes or backend port 8132. Record results and logs without copying tokens, enrollment codes or guest data into the report.

No TCL online PASS is claimed by this audit. Proxy installation, DNS, firewall and certificate work require a separately approved pilot step.
