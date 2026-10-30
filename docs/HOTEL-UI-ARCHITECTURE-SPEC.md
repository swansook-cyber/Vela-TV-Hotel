# Phase 1 design specification for Phase 2

## Structure

Retain the Hotel backend, CMS, credentials, media sync, cache, enrollment and manifest contracts. Rebuild only the Android presentation layer around reusable components: \`TvShell\` (safe area and common header), \`FocusCard\` (border/lift animation), \`AppArt\` (shape-checked banner/icon), \`SectionGrid\`, \`QuickAppsRow\`, and \`DetailPanel\`. Keep Quick Apps assignment in the existing local preferences.

## Home

Use the original Ao Nang Fiore Resort logo with aspect-preserving FIT_CENTER, a generic or assigned-room greeting, one row of four section cards, one row of five 16:9 Quick Apps, and a compact utility/status row. Set the four destinations in the requested order. Missing apps show \`Not Installed\` and remain focusable for editing their slot. A long OK press or \`Edit Apps\` opens the editor. Never register a HOME intent.

## Subpages

Share a fixed header and back affordance. Use two- or three-column cards with short titles and category hints; select a card to open a detail panel. Entertainment shows installed TV apps as artwork cards. Empty states use one concise panel. Save the source card/slot before navigating so Back returns focus there.

## Geometry and motion

For a 1280×720 dp viewport, reserve at least 48dp horizontal and 36dp vertical inset; calculate equivalent proportional margins at other viewports. Home must fit within the visible frame without relying on vertical scroll. Primary text 24–30sp, card titles 18–21sp, supporting text 14–16sp. App art box 16:9; use FIT_CENTER for banners and a centered icon fallback. Use a 3dp light focus border, slight elevation and at most 1.04 scale. Keep 8–16dp between cards so the focused outline does not collide. Avoid large yellow fills.

## Verification gates

Capture emulator Home plus Entertainment, Our Resort, Explore Krabi, Guest Information, detail, Quick Apps editor/picker, and empty state at 720p and 1080p. Record D-pad/OK/Back focus routes, shortcut reassignment, external-app return, clipping and text truncation. Build and inspect the APK manifest; require GitHub Actions PASS. Physical TV QA remains a separate owner gate.
