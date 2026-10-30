# Phase 1 — Vela TV Launcher V4 UI analysis

Date: 30 October 2026 (Asia/Bangkok)

## Identity and evidence

| Target | Branch and HEAD | State |
| --- | --- | --- |
| Source \`swansook-cyber/Vela-tv\` | \`feature/vela-tv-v4-apple-home-themes\` · \`af937d3034aeab9043cf8341cba16f8ba53b1788\` | Clean; read-only checkout |
| Hotel baseline \`swansook-cyber/Vela-TV-Hotel\` | \`fix/hotel-v1-tv-layout-all-pages-20261030\` · \`6eda65d34a05da6ea1d493cb73926329fbdb2a5e\` | Clean |

The V4 source built a debug APK successfully. REA opened that APK (SHA-256 \`aae35132458f40d9e630125c32dffa4b4592588f1dd55dca86dc48d61b3e8a0f\`) and inventoried all 106 APK entries. REA Evidence \`ev_560fe03abf3778712c892f3dccb611b8f581abb28255644845fa5fce7bd4eb41\` / inventory \`ev_be729e0cd8d743df3081006b4aa47bff8f07ba0fcd91efb09208ac4b22380a10\` confirms \`classes.dex\`, \`classes2.dex\`, \`classes3.dex\`, \`res/layout/activity_main.xml\`, \`res/layout/item_app.xml\`, drawables and animation resources in the shipped debug package. This is package inventory, not runtime behavior.

REA's Android class decompiler was unavailable: \`inspect_android_package\` requires a caller-supplied \`jadx-headless-mcp 0.7.1\` JAR through \`REA_JADX_MCP_JAR\`. No such JAR was found locally. The code conclusions below are therefore from the matching source checkout, corroborated by the APK resource inventory. They are not claims from decompiled DEX or observed TV execution.

## Architecture observed in V4 source

| Area | Observation | Evidence in source |
| --- | --- | --- |
| Home | \`MainActivity\` owns a layered full-screen root: sky/background, optional hero art and scrim, top information block, a RecyclerView grid, and a floating glass focus view. | \`launcher/app/src/main/res/layout/activity_main.xml\`; \`MainActivity.java\` lines 373–630 |
| App art | \`AppAdapter\` first tries configured bundled banners, then activity/application banner. It rejects square banner assets (ratio below 1.4) and falls back to an icon. Tile artwork uses \`fitCenter\`. | \`AppAdapter.java\` lines 294–369; \`res/layout/item_app.xml\` |
| Size | Design tile is 180×101 dp (roughly 16:9), with a 1.08 focus scale. Width is bounded by the available grid cell and height can reduce it to fit visible rows, with a minimum 85% of the width-fit size. | \`AppAdapter.java\` lines 98–170 |
| Focus | RecyclerView GridLayoutManager, explicit row-edge handling, vertical moves by column count, last focused position/key, restore after reload, and a floating glass highlight that follows the focused card. | \`MainActivity.java\` lines 497–630, 997–1105, 1584–1680, 2190–2242; \`GridNavigation.java\` |
| Motion | Card scale/translation plus label fade on focus. Configurable reduced-motion path shortens motion to about 90 ms. | \`AppAdapter.java\` lines 887–949; \`Motion.java\` |
| Apps/order | Leanback apps and selected standard launcher apps are merged, hidden/added/removed filters applied, then sorted by saved order, configured favorites, module cards and label. \`Order\` stores validated private order via AtomicFile. | \`AppAdapter.java\` lines 253–340; \`Order.java\` |
| Return | On resume, package changes trigger tile rebuild and the previous app/slot is refocused. | \`MainActivity.java\` lines 910–1105 |

The V4 source also includes HOME and accessibility integrations that belong to the personal launcher. They are outside the Hotel app and must not be copied.

## Hotel baseline comparison

The Hotel baseline keeps CMS, media sync, encrypted device credentials and offline cache in separate Kotlin classes. Its UI is still a single programmatic \`MainActivity\`: a ScrollView with two rows of full-text Button navigation, a compact five-slot shortcut row, and full-width Button lists on subpages. Focus changes a button's full fill to gold. The Quick Apps editor already stores five package choices locally and uses installed Leanback activities; it must be preserved and restyled, not rebuilt from scratch.

| V4 strength to adapt | Hotel baseline gap or risk | Hotel-specific direction |
| --- | --- | --- |
| Banner shape check and icon fallback | Hotel treats any loaded banner as wide and can compress square art | Validate intrinsic ratio, keep 16:9 art box, center icons |
| Grid geometry and focus-visible padding | Fixed heights and full-width buttons make 720p/subpages dense | Measure viewport; use card grid and scroll only where needed |
| Stable app identity and focus return | Home remembers only one of four main buttons; subpage/item return is not kept | Save page, item and shortcut slot focus identity |
| Small focus zoom + separate highlight | Gold fill obscures artwork and text | White/teal ring, subtle lift, no full-card yellow |
| Atomic order storage | Hotel Quick Apps uses SharedPreferences and already has local editing | Preserve choices, make editor remote-friendly; no HOME behavior |

## Apple TV visual reference

Apple's published guidance recommends clear focus, spacing that accommodates focused enlargement, legible content at viewing distance and safe margins. Its tvOS layout guidance uses 80 points at the sides and 60 points top/bottom as a reference. These are **reference principles**, not Android dimensions or copied Apple assets. See [Layout](https://developer.apple.com/design/human-interface-guidelines/layout), [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection/), and [Designing for tvOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos/).

## Unknowns and boundaries

- Static source and APK inventory do not prove actual focus, frame timing, clipping or artwork rendering on a physical TV.
- V4 was built as a local debug APK; this does not establish equivalence with a signed production V4 APK.
- The Hotel baseline's actual 720p/1080p screenshots and D-pad paths must be measured in an emulator before calling the rebuild better.
- Apple TV design guidance is a visual reference only; no Apple code, imagery, font or trademark is to be imported.
