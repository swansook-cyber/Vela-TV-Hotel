#!/usr/bin/env bash
set -euo pipefail
# Operator-run only after separate approval for staging installation.
test "${1:-}" = --confirm-install || { echo 'Usage: sudo ./install.sh --confirm-install'; exit 2; }
test "$(id -u)" -eq 0 || { echo 'Run as root'; exit 2; }
repo="$(git rev-parse --show-toplevel)"
cd "$repo"
test -z "$(git status --porcelain)" || { echo 'Source checkout must be clean'; exit 2; }
sha="$(git rev-parse HEAD)"
test "$(git branch --show-current)" = feature/hotel-v1-homehub-staging-20261030 || { echo 'Unexpected branch'; exit 2; }
test ! -e /etc/systemd/system/vela-tv-hotel-staging.service || { echo 'Service already exists; use documented upgrade/rollback procedure'; exit 2; }
test -f /etc/vela-tv-hotel-staging.env || { echo 'Create private environment file first'; exit 2; }
test "$(stat -c %a /etc/vela-tv-hotel-staging.env)" = 640 || { echo 'Environment file must be mode 0640'; exit 2; }
test "$(stat -c %U /etc/vela-tv-hotel-staging.env)" = root || { echo 'Environment file must be owned by root'; exit 2; }
/usr/bin/python3.11 --version
id vela-hotel-stg >/dev/null 2>&1 || useradd --system --home-dir /var/lib/vela-tv-hotel-staging --shell /usr/sbin/nologin vela-hotel-stg
test "$(stat -c %G /etc/vela-tv-hotel-staging.env)" = vela-hotel-stg || { echo 'Environment file group must be vela-hotel-stg'; exit 2; }
install -d -m 0755 /opt/vela-tv-hotel-staging/releases
install -d -m 0700 -o vela-hotel-stg -g vela-hotel-stg /var/lib/vela-tv-hotel-staging
release="/opt/vela-tv-hotel-staging/releases/$sha"
test ! -e "$release" || { echo 'Release exists'; exit 2; }
install -d -m 0755 "$release/backend" "$release/docs"
install -m 0644 backend/*.py "$release/backend/"
install -m 0644 docs/hotel-content-sample.json "$release/docs/"
ln -s "releases/$sha" /opt/vela-tv-hotel-staging/current
install -m 0644 deploy/homehub-staging/vela-tv-hotel-staging.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now vela-tv-hotel-staging.service
echo "Installed staging release $sha. Run verify.sh; tunnel configuration remains manual."
