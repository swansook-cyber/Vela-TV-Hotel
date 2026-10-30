#!/usr/bin/env bash
set -euo pipefail
systemctl is-active --quiet vela-tv-hotel-staging.service
ss -H -ltn '( sport = :8131 )' | grep -q '127.0.0.1:8131'
test "$(stat -c %a /var/lib/vela-tv-hotel-staging)" = 700
test "$(stat -c %a /etc/vela-tv-hotel-staging.env)" = 640
curl --silent --show-error --fail http://127.0.0.1:8131/healthz >/dev/null
for path in /api/v1/device/manifest /api/v1/admin/status; do
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:8131$path")"
  test "$status" = 401 || { echo "Unexpected $status on $path"; exit 1; }
done
echo 'PASS: loopback service, private paths, health, unauthorized API checks'
