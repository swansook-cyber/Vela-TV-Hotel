# Fiore Premium UI V1.0.5 — internal QA

Baseline: V1.0.4 `515d877f896c7011d47a2b6918035f7bfb5ac0e8`. Version name `1.0.5`, version code `13`.

## Image and content decisions

Home uses the existing official Ao Nang Fiore Resort image seed in local internal QA builds. The hero remains the resort aerial image, under two dark gradients. The four Home image cards use: pool for Entertainment, hero aerial for Our Resort, a second aerial resort view for Explore Krabi, and a room photo for Guest Information. The Explore card is a resort-context backdrop; it does **not** depict or claim to depict a Krabi attraction. Without the ignored local seed, CI uses the existing illustrative fallback assets. The four images, their exact source URLs, rights status and checked date are recorded in [the V1.0.3 seed inventory](FIORE-WEBSITE-SEED-V1.0.3.md).

Website image reuse remains unconfirmed. Do not commit the `fiore_internal` directory, a local photo-bearing APK, or screenshots with those photos to a public repository. The Android CI checks that the directory is absent before building its public fallback artifact. Local photo-bearing debug APKs are for internal QA only.

The verified offline content descriptions retain source and check-date notes in `hotel_content.json` for audit. The guest-facing UI omits the trailing `Source:` note. Release builds omit the internal QA footer; debug builds identify official-site photos or illustrative fallback. No prices, live offers or mutable hotel facts were added.

## Layout and focus

- Home has four image cards with bottom-aligned translucent caption panels. Existing header, weather position, menu order, Quick Apps and staff access remain.
- Main and Resort photo cards scale to `1.045` on focus over `150 ms`, with a light outline and elevation. Card margins reserve space for this scale; animated views do not change layout size.
- Resort photo cards are taller (`245 dp` normal, `168 dp` compact). Their labels remain attached to the image bottom.
- Weather condition and last-update text are larger. MET Norway request, cache, refresh and timezone code are unchanged.
- The large hero bitmap is cached per Activity so navigation does not decode it again on each Home return.

## Verification and limits

Run Android unit tests and `assembleDebug`, then check the actual APK version and signing certificate. On emulator, check 720p, 1080p and 4K AVD Home and Resort; D-pad, OK, Back/focus restoration, long-press OK on Quick Apps, and weather online/cached states. Keep screenshot evidence outside the public repository while photo rights are unconfirmed.

The 4K AVD reports a physical 3840×2160 display but applies a 1920×1080 Android logical override. It is layout evidence, not native 4K proof. The 1080p emulator uses SwiftShader software OpenGL; `gfxinfo` frame timing there cannot establish TCL hardware smoothness. Verify focus motion, overscan and external-app return on the physical TCL before release. No default launcher setting is changed.

Local QA on 2026-10-31: 1080p and 720p Home and Our Resort were visually checked for clipping; 4K AVD Home was checked at its logical 1080p output. D-pad/OK opened Our Resort; Back restored the Home card focus; D-pad/OK opened a resort detail and Back restored that photo-card focus. Long-press OK opened the unchanged Quick Apps editor. The live widget showed a MET Norway reading and Last updated time; the inherited weather unit tests cover timeout, HTTP error, cached fallback and Bangkok time. A reliable emulator network cutoff was not established, so no offline screenshot is claimed.

On the 1080p emulator, total PSS was 47.5 MB on Home, 60.0 MB after one Resort round trip, and 50.9 MB after repeated Home focus moves. A 16-transition focus sample recorded 144 frames, median 44 ms and 47.22% janky frames under SwiftShader. This **does not pass a hardware smoothness gate**; test on TCL before accepting performance. The focus animation is limited to scale and elevation at 150 ms, with fixed card bounds and no layout reflow.
