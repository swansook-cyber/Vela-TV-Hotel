# Windows development server — V1.1.0

This is a **local development** setup, separate from the Windows hotel pilot. It does not install a Windows service, change DNS or Firewall, or expose the admin API beyond loopback. Do not copy its test database, tokens or enrollment codes to the hotel server.

## Workspace layout

`backend/.hotel-data/` is Git-ignored. Keep `development-venv/`, `development.env`, `development-data/`, `development-backups/` and any restored QA snapshots there. The approved initial draft is read from `android-client/src/main/assets/hotel_content.json`; do not edit that seed for server QA.

## Start locally

1. Use Python 3.12 already installed on the development PC. Create `backend/.hotel-data/development-venv` with `python -m venv` and use **that venv's** `Scripts/python.exe -m pip install -r backend/requirements-windows.txt`. Optional browser QA tools belong only in the same development venv.
2. Create an ignored `backend/.hotel-data/development.env` containing `HOTEL_PROFILE=development`, absolute paths for `HOTEL_DATA_DIR` and `HOTEL_INITIAL_CONTENT`, `HOTEL_BIND_PORT=8132`, a valid hostname for `HOTEL_PUBLIC_MEDIA_HOST`, and two distinct random 32+ character editor/publisher tokens. Restrict the file ACL to the current developer, Administrators and SYSTEM. Do not print, commit or reuse these tokens. The hostname is only a contract value until HTTPS/DNS are configured; it does not make this loopback server reachable by a TV.
3. Check with `backend/.hotel-data/development-venv/Scripts/python.exe backend/pilot_runtime.py --config backend/.hotel-data/development.env --check`, then run the same command without `--check` in a dedicated terminal. Stop it with Ctrl+C. It binds only `127.0.0.1`.
4. Open `http://127.0.0.1:8132/admin` **on the same PC**, authenticate as `editor` or `publisher` with the corresponding local development token, and close the browser session after QA. Do not access this HTTP endpoint from another machine. `/healthz` and `/readyz` provide local checks.

## QA and backup

Use the CMS to Save Draft, Preview, Validate and Publish. Draft saves do not change the published manifest. Publish a second local revision and Rollback to the first to test history. Enrollment codes and device tokens from this development database are test-only. For a backup, run `backend/pilot_backup.py backup <absolute-development-data> <new-archive.zip>` with the development venv interpreter; restore only into a **new** directory and verify SQLite integrity and the published revision. Never overwrite the live development directory during a restore test.

## Future TCL HTTPS path

For network QA, prepare a hotel-owned hostname with split DNS to the designated pilot host, a publicly trusted certificate whose SAN matches that hostname, and a TLS reverse proxy on 443. Route only device/content/media paths to the loopback backend on the TV hostname. Put Admin behind a separate staff-only hostname and network ACL. Keep Android certificate verification enabled. Confirm from a TCL that DNS, TLS chain and hostname validation work before enrollment. The current development PC has **no** DNS, TLS or Firewall changes for this purpose; `http://127.0.0.1:8132` is not a TCL endpoint. Follow [Windows pilot installation and rollback](WINDOWS-HOTEL-PILOT-V1.1.md) only after separate installation approval.
