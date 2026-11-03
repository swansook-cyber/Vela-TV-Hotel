# Fiore approved content integration

Source: `638830211362193836_Vela_TV_Hotel_Fiore_Content_Form.docx` supplied by the resort. SHA-256: `3183e5e2acfb9e1f72c30492c0ca0e3702e4b98748475f590923f83b9fcad93a`. The source document is not committed. The user confirmed that the completed form's content is approved for development; the form itself leaves the reviewer, approval date, media rights certification, and final sign-off fields blank.

The offline seed is `android-client/src/main/assets/hotel_content.json`. `approved_content` holds the hotel's English and Thai text apart from Android views. Existing `resort`, `explore`, and `guest` items remain valid under the current CMS content contract. A published CMS document can replace the seed; the Android UI uses the structured details when present and retains the existing item view when absent. No backend or API change is required.

## Section mapping

| Form section | Status | App destination or handling |
| --- | --- | --- |
| A. Contact and approval | requires_operational_verification | Hotel name only in seed; staff name, personal phone, sales email, and signature omitted from guest content. Reviewer and dates are blank. |
| B. Home / Welcome | approved, with missing fields | Generic “Welcome to Aonang Fiore Resort” and exact tagline on Home. Guest name, custom welcome copy, and image filenames are blank. Resort logo and Fiore24 hero remain unchanged. |
| C. About the Resort | approved | Exact English and Thai paragraphs in `approved_content.about`; English summary and a focusable detail page under Our Resort. Thai paragraph and Thai highlights on detail page. Service list is retained in seed but not used to invent service details. |
| D. Accommodation | approved, with missing fields | Three room descriptions and sizes on Rooms & Amenities detail page. Fiore24 room gallery is grouped by category; no image is assigned to a specific room type. Occupancy and individual image mappings are missing. The ambiguous `13` mentioned in the request is not imported as an amenity. |
| E. Dining | approved, requires_operational_verification | De’fish Restaurant, cuisine, hours, and extension 4003 in Dining detail. Second restaurant entry is an untouched form placeholder. Hours have no review date. |
| F. Spa, Pool & Activities | approved, requires_operational_verification | Pool and Palm tree massage hours, and massage contact 4006 or 0, in Facilities detail. #355 stays the category cover. Activities and safety rules are unfilled. Image #407 remains a facility image with no spa-service claim. |
| G. Explore Krabi | missing | `explore.items` is empty; existing designed empty state appears. No attraction list or travel time is inferred. |
| H. Guest Information | approved, requires_operational_verification | Check-in, check-out, Reception, Housekeeping, Shuttle, no-smoking, free Wi-Fi/no password, and emergency contact as entered. Shuttle schedule has no review date. No additional Wi-Fi network name or credential is included. |
| I. FAQ | missing | No FAQ cards are created. All five entries are form placeholders. |
| J. Contact / Emergency | approved, with missing fields | Hotel address in English and Thai, main telephone, guest email, and website. Map URL and social channels are blank. |
| K. Media and rights | requires_operational_verification | Media filename and rights fields are blank. Existing Fiore24 remains opt-in for internal QA only under its separate asset index; no new image is imported. |
| L. Frequently changing data | requires_operational_verification | Owner, review cadence, promotions, and closure notices are blank. Service hours in E/F/H need an owner and review date before broad release. |
| M. Acceptance checklist | missing | All checkboxes and reviewer/date fields are blank. Retain as a release gate. |
| N. Developer handoff | guidance | The seed follows the form's missing-data and credential policy. No server or deployment changes. |
| O. Resort certification | requires_operational_verification | Name, role, signature, and date lines are blank. The user's development authorization applies to this integration, not to production release or media redistribution. |

## Verification and release limits

- The complete DOCX body, including all tables in A–O, was extracted. Microsoft Word exports this supplied file as eight pages, despite the ten-page count in the request. No section was skipped.
- The guest display uses the room-name capitalization, formatted emergency number, and expanded English address supplied in the request. The document's original variants are retained in `source_name`, `source_emergency`, and `source_address_en` for comparison.
- Fiore24 WebP assets, gallery category lists, HOME and LEANBACK_LAUNCHER filters, Quick Apps, and the backend were not modified.
- The seed has no room occupancy, prices, promotions, guessed activities, guest name, Wi-Fi network identifier, password, API key, or personal staff contact.
- Physical TCL focus, clipping, language legibility, and scrolling remain to be tested before an APK or room rollout.
