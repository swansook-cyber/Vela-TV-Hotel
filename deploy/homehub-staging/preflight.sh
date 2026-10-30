#!/usr/bin/env bash
set -euo pipefail
# Read-only inspection. This script never changes the Home Hub.
test "$(id -u)" -ne 0 || echo 'INFO: root is unnecessary for preflight'
source /etc/os-release
printf 'OS: %s %s\n' "$ID" "$VERSION_ID"
test "$ID" = debian && test "$VERSION_ID" = 12
/usr/bin/python3.11 --version
for binary in systemctl ss tar sha256sum curl; do command -v "$binary" >/dev/null; done
if ss -H -ltn '( sport = :8131 )' | grep -q .; then
  echo 'BLOCKED: TCP port 8131 is occupied'; exit 1
fi
for path in /opt/vela-tv-hotel-staging /var/lib/vela-tv-hotel-staging /etc/vela-tv-hotel-staging.env /etc/systemd/system/vela-tv-hotel-staging.service; do
  if test -e "$path"; then printf 'EXISTS: %s (review before installation)\n' "$path"; fi
done
printf 'Available disk bytes: '
df -PB1 /var/lib | awk 'NR==2 {print $4}'
echo 'PASS: read-only preflight; inspect any EXISTS entries.'
