#!/usr/bin/env bash
set -euo pipefail
test "${1:-}" = --confirm-rollback && test -n "${2:-}" || {
  echo 'Usage: sudo ./rollback_release.sh --confirm-rollback <previous-commit-sha>'; exit 2;
}
test "$(id -u)" -eq 0 || { echo 'Run as root'; exit 2; }
sha="$2"
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid commit SHA'; exit 2; }
root=/opt/vela-tv-hotel-staging
test -f "$root/releases/$sha/backend/hotel_api.py" || { echo 'Release missing'; exit 2; }
test -L "$root/current" || { echo 'Current release is not a symlink'; exit 2; }
old="$(readlink "$root/current")"
ln -s "releases/$sha" "$root/next"
mv -Tf "$root/next" "$root/current"
if ! systemctl restart vela-tv-hotel-staging.service; then
  ln -s "$old" "$root/next"
  mv -Tf "$root/next" "$root/current"
  systemctl restart vela-tv-hotel-staging.service
  echo 'Rollback failed; original release restored'; exit 1
fi
echo "Staging code now points to $sha. Database/content was not changed."
