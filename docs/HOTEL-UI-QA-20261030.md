# Vela TV Hotel UI rebuild — emulator QA

Baseline: `fix/hotel-v1-tv-layout-all-pages-20261030` at `6eda65d34a05da6ea1d493cb73926329fbdb2a5e`.
Rebuild: `feature/hotel-v1-rea-ui-rebuild-20261030`.

## Before / after

| Area | Baseline observation | Rebuild observation |
| --- | --- | --- |
| Home | Two rows of broad text buttons; focused button fills gold; subtitle clips at 720p | One row of four navigation cards; white focus outline; all card text fits at 720p and 1080p |
| Quick Apps | Five slots in one row | Five slots remain in one row, with original TV banners where available, `Not installed` status, remote editing |
| Resort, Explore, Guest | Full-width buttons | Three-column content cards, detail panel, section header and Back |
| Entertainment | Text buttons | TV app artwork cards in four columns |
| Media | Text-only empty message | Shared header and empty-state panel |
| Connection | Standalone title and buttons | Shared header and compact connection form |

Screenshots in `docs/qa-screenshots/` were captured from AOSP TV emulators, with 1280×720 at 160 dpi and 1920×1080 at 240 dpi. `baseline-*.png` came from the baseline APK, and the remaining files came from the rebuilt APK. No physical TV test is claimed.

## Validation

- Android `assembleDebug`: pass.
- Backend unit and integration suite: 19 tests pass.
- UI hierarchy D-pad test: pass. Home Right focuses Our Resort; section Down/Up traverses card rows; detail Back restores Rooms & amenities focus; section Back restores Our Resort focus; Quick Apps is reachable and long OK opens editor.
- Quick Apps test: slot changed to installed Live TV, displayed on Home, then restored to defaults. Installed YouTube opens. On explicit return to Hotel, focus remains on YouTube slot 3.
- Visual review: Home, four main sections, detail, media, setup, Quick Apps editor and picker at both resolutions show no cut edges or clipped main labels.
- APK manifest: `net.velalab.tvhotel`, version name `1.0.2`, version code `10`.
- Original resort logo was not modified.
- Android manifest exposes only `LEANBACK_LAUNCHER`; it does not register HOME.

## Limitations

- The emulator YouTube app consumes Back inside its own task, and Android may return to the emulator's previous task. The Hotel app cannot guarantee that third-party Back exits directly to Hotel. Reopening Hotel preserves the selected Quick App focus. This must be checked on the target TV devices.
- Resort content shown is explicitly marked `TEST DATA`; real hotel data and approved media are pending.
- Physical Xiaomi TV Box S 2nd Gen and Google TV verification remains for the owner.
- REA APK decompilation was unavailable because this host lacks the REA JADX 0.7.1 jar. The source architecture analysis and REA APK inventory are documented separately.

## Physical TV QA

Install the debug APK through ADB or the device's normal test sideload flow. Open **Vela TV Hotel** from Apps. Do not change the device's default launcher. Test D-pad Right/Down/Up, OK and Back on every page; hold OK on a Quick App; change and restore a slot; launch an installed streaming app and return; verify overscan and legibility at 720p and 1080p display modes. Record TV model, Android version, display mode and result. No production endpoint or credentials are required for offline UI QA.

## Rollback

For UI-only rollback, rebuild the baseline branch with the same debug signing key and install the lower version code with `adb install -r -d` (where the device permits debug downgrade). Keep app data intact so published content, enrollment and cache remain available. Restore the development branch by reinstalling the rebuilt APK. Do not use `pm clear` or uninstall as a rollback method.
