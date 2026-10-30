# Fiore Luxury TV — premium UI QA

Baseline: Android V1.0.2, version code 10, `feature/hotel-v1-rea-ui-rebuild-20261030` at `7ecb8b53aaedf2210078f6aeb6ade3cf7bce8efc`.

## Changes and image provenance

- Home uses the original Ao Nang Fiore Resort logo, an offline conceptual tropical hero, dark gradients, the existing four guest destinations, and the existing five editable Quick Apps.
- Installed apps show their TV banners when available or aspect-preserving app icons. Missing apps show a muted icon and “Not installed.” Focus uses a light outline and transform scale; row layout dimensions remain fixed.
- Our Resort shows About the Resort plus four photographic concept cards for Rooms & amenities, Restaurants & dining, Spa/pool/activities and Special Offers. Titles and details still come from the existing CMS/content model. Additional published items remain reachable in subsequent rows.
- The images `fiore_concept_hero.png` and `fiore_concept_resort_grid.png` were generated with the built-in imagegen tool for this UI as **fictional offline placeholders**. Neither is a photograph of Ao Nang Fiore Resort. The Home footer and each photo card label them as conceptual imagery. The original resort logo remains unchanged.
- Imagegen prompts: (1) fictional Krabi-inspired luxury tropical resort at blue hour, cinematic 16:9 landscape with dark space for TV UI, no people, logos or signage; (2) a 2×2 fictional resort concept sheet with room, open-air dining, spa/pool and offer still life, no people, logos, signage or text.
- Guest Home no longer shows operational controls. On Home, focus **Guest Information** and hold OK to open **Staff Tools**; Back returns Home. Staff Tools contains Edit Apps, Resort Media and Connect / Sync. Holding OK on a Quick App still opens its editor directly, preserving remote shortcut editing.

## Emulator evidence

| Capture | AVD | Display |
| --- | --- | --- |
| [Home 1080p](qa-premium-screenshots/home-1080p.png) | AOSP TV Android 34 | 1920×1080, 240 dpi |
| [Our Resort 1080p](qa-premium-screenshots/resort-1080p.png) | AOSP TV Android 34 | 1920×1080, 240 dpi |
| [Staff Tools 1080p](qa-premium-screenshots/staff-tools-1080p.png) | AOSP TV Android 34 | 1920×1080, 240 dpi |
| [Home on 4K AVD](qa-premium-screenshots/home-4k-avd-ui1080.png) | AOSP TV Android 34, physical display 3840×2160 | Android logical UI and screenshot 1920×1080, 320 dpi |
| [Our Resort on 4K AVD](qa-premium-screenshots/resort-4k-avd-ui1080.png) | Same 4K AVD | Android logical UI and screenshot 1920×1080, 320 dpi |

The 4K AVD exposes a 3840×2160 physical display but Android TV applies a 1920×1080 logical UI override. The captures are labeled accordingly; they do not prove native 3840×2160 UI rendering or physical TV overscan. Visual review of both display modes found no card or text cut off. Resort card titles initially truncated at the 4K AVD's logical density; allowing two lines at compact widths resolved this in the final captures.

## Remote smoke test

Performed with `adb shell input keyevent` and `uiautomator dump` on the 4K AVD:

1. Home Right focused Our Resort; OK opened Our Resort and focused About the Resort. OK opened About; Back restored focus to About.
2. Down reached the photo-card row; D-pad navigation reached Restaurants & Dining. OK opened its detail. Back returned focus to Restaurants & Dining. Another Back returned Home with Our Resort focused.
3. Home Down reached Quick Apps. Holding OK on Disney+ opened Customize Quick Apps with the same slot focused.
4. Holding OK on Guest Information opened Staff Tools with Edit Apps focused. Back returned Home.

The focus border and slight scale did not change row spacing in the reviewed captures. The Home safe inset is at least 48dp and scales to approximately 5% of the viewport width. No backend, CMS, API, enrollment, deployment, default HOME intent, or Quick Apps storage logic changed.

## Limits and physical TV checklist

The emulator is not Xiaomi TV Box S 2nd Gen or a physical Google TV. Before accepting the design for a hotel pilot, install the CI debug APK on each target TV without changing default HOME; check 1080p and 4K display modes, overscan, text legibility from guest viewing distance, every D-pad/OK/Back route, long OK on Guest Information and Quick Apps, installed app banners, external app return focus, and offline Our Resort cards. Do not publish the APK or roll out to guests before owner approval.
