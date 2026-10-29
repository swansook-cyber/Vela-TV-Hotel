# Hotel content input checklist

Use [hotel-content-sample.json](hotel-content-sample.json) as the import template. Every unverified item is marked `TEST DATA` and `verified: false`; replace only with resort-approved facts. A saved draft is never published automatically.

## Required before guest rollout

- [ ] Confirm resort display name, original logo rights, approved greeting, brand colors and photography.
- [ ] Confirm room categories, amenities, access instructions and guest services. Assign each actual room only in the admin device map.
- [ ] Confirm restaurant names, menus, service hours, booking methods and any charges.
- [ ] Confirm spa, pool, shuttle, parking and activity hours, conditions and contacts.
- [ ] Confirm reception phone or extension, check-in/out, housekeeping, policies and approved emergency instructions.
- [ ] Enter guest Wi-Fi instructions in the private CMS. Do not place a Wi-Fi password in Git.
- [ ] Approve Krabi attractions, operators, transport advice, prices and usage rights.
- [ ] Approve promotions with start/end dates and terms.
- [ ] Supply hotel-owned image/video files with rights, format, size and playback approval.
- [ ] Approve Thai/English translations and default welcome copy.

## Pilot workflow

1. Copy the sample JSON. Keep the schema version and fixed Quick Apps order.
2. Update a draft through `POST /api/v1/admin/draft` or the staging CMS. Unverified descriptions must contain `TEST DATA`.
3. Preview the exact draft with `GET /api/v1/admin/preview`.
4. Upload approved media; configure a playlist referencing its SHA-256 and HTTPS URL.
5. Publish with a publisher account. Verify a pilot TV fetches the new revision.
6. Record the revision ID. Roll back by selecting a prior ID if needed.

Shared published content uses only generic welcome text. Room labels and guest identity must never be added to the import JSON.
