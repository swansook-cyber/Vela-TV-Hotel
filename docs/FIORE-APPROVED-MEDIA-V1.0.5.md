# Fiore V1.0.5 owner-selected media

This branch contains 24 owner-selected photos from `D:\aonangfiore\Photo` for
**internal QA**. `media/fiore-approved/assets/fiore_approved/index.json` records
each original filename and SHA-256, export SHA-256, size and category. The
original JPEGs were read only. Redistribution rights have not been documented.

The WebP exports are 1920 × 1080 or smaller, maintain the image proportions
through a center crop, and contain no EXIF/GPS metadata. The photo preparation
script checks all 24 originals against the approved `selected-29.csv` before
writing exports. `#407` is described only as a facility interior; its service
identity and availability need confirmation.

| UI placement | Approved photo numbers |
| --- | --- |
| Welcome hero | 18 (primary), 28 (About the Resort gallery) |
| Rooms & Amenities | 1 (cover), 5, 37, 130, 164, 169, 200 |
| Restaurants & Dining | 234 (cover), 227, 243, 251 |
| Spa, Pool & Activities | 355 (approved cover), 346, 407, 412 |
| Guest Information / About gallery | 321, 336 |
| Resort Grounds / About gallery | 430, 435, 437, 452, 456 |

The remaining approved photos appear in the relevant Our Resort detail gallery.
No resort photo is presented as an off-site Krabi attraction or as a current
promotion. CMS-published content and synced media behavior remain independent.

The owner-provided square and horizontal PNGs become the launcher icon,
adaptive icon foreground and 640 × 360 Android TV banner. The original logo
files remain in `D:\aonangfiore`; the generated Android resources have no EXIF.

Public CI deliberately keeps its existing fallback asset profile. For a later
**internal QA** build, explicitly pass `-PincludeApprovedFioreMedia=true` to
Gradle. No APK was built during this integration task. The local ignored
`android-client/src/main/assets/fiore_internal` directory contains earlier
website photos and is not part of this commit. Gradle now stages only
`hotel_content.json` from the canonical asset directory, so the ignored legacy
directory stays outside both public and opt-in QA asset trees without deletion.

The branch's CI workflow builds an APK on ordinary pushes. This integration
commit uses `[skip ci]` because the owner explicitly deferred all APK builds.
Run Android CI only when the owner issues the later build instruction.
