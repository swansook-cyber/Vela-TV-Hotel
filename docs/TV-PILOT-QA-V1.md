# Physical TV pilot checklist (pending human test)

Test on Xiaomi TV Box S 2nd Gen and Google TV at 720p and 1080p. Record device firmware, display resolution, APK filename/hash, revision and tester/date. Do not mark this gate passed without a person testing the actual devices.

- [ ] Install V1.0 debug APK without selecting it as default HOME.
- [ ] Confirm original resort logo, welcome and four destinations fit within safe margins.
- [ ] Navigate every card and Quick App using D-pad, OK and Back; focus must remain visible.
- [ ] Verify fixed app order and genuine installed app icons; absent apps display Not Installed and cannot launch.
- [ ] Enroll with a one-time code through Connect / Sync. Check successful publish, refresh and last update.
- [ ] Verify draft edits remain invisible until publish and rollback restores the earlier revision.
- [ ] Start offline, disconnect during download, return HTTP 401/403, serve a corrupt asset, an empty/invalid manifest and fill storage. Prior verified content must remain available.
- [ ] Reboot during sync and verify recovered media. Check image aspect ratio and MP4 hardware playback, including malformed codecs.
- [ ] Revoke and re-enroll a device. Confirm another TV cannot use its token.
- [ ] Check room mapping and generic welcome. No real guest identity should appear in the APK or shared content.
