#!/bin/sh
# Application smoke tests against the real Traefik Ingress
# (ooredoo.localhost, mapped to host port 8081 by k3d) - never against a
# port-forward. Uses `curl --resolve` instead of a hosts-file entry so it
# needs no admin/host changes on the runner. Fails (non-zero exit) on the
# first mandatory check that doesn't behave as expected - this script's
# exit code gates the calling GitHub Actions step.
#
# Assumes the Phase 4 seeded test admin (admin@local.tn) already exists in
# the staging MongoDB - it is real, persistent staging data (survives
# every helm upgrade since Phase 4), not something this script creates or
# rotates. If login fails because that account is genuinely gone, that is
# itself a real regression this script should report, not paper over by
# minting a new account.
set -eu

BASE="http://ooredoo.localhost:8081"
RESOLVE="--resolve ooredoo.localhost:8081:127.0.0.1"

# Deliberately not mktemp/$TMPDIR: some machines repurpose TMPDIR to a
# location the Python interpreter can't write into even though the shell
# can create directories there. A fixed subdirectory next to this script,
# cleaned up on exit, is portable across every environment this runs in.
#
# `pwd -W` (Git Bash's own drive-letter form, "C:/path/like/this") rather
# than plain `pwd` ("/c/path/like/this"): this project's curl.exe and
# python.exe are native Windows builds, not MSYS ones - both reject the
# /c/... form for file arguments (curl: "couldn't open file", exit 26;
# python: FileNotFoundError) even though bash's own mkdir/rm/redirects
# accept either form fine. Using the -W form everywhere sidesteps the
# mismatch instead of needing two different path variables.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -W)
WORKDIR="$SCRIPT_DIR/.smoke-test-tmp"
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
trap 'rm -rf "$WORKDIR"' EXIT

fail() { echo "SMOKE TEST FAILED: $1" >&2; exit 1; }
pass() { echo "OK: $1"; }

# 1. Frontend health
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' "$BASE/")
[ "$status" = "200" ] || fail "frontend health: expected 200, got $status"
pass "frontend health (200)"

# 2. SPA fallback (unknown client-side route still serves index.html)
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' "$BASE/some/client/route/that/does/not/exist")
[ "$status" = "200" ] || fail "SPA fallback: expected 200, got $status"
pass "SPA fallback (200)"

# 3. Login (valid)
login_response=$(curl -s $RESOLVE -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@local.tn","password":"admin123"}' \
  "$BASE/api/v1/auth/login")
TOKEN=$(echo "$login_response" | python -c "import json,sys; print(json.load(sys.stdin)['token'])" 2>/dev/null) \
  || fail "login: could not extract token from response: $login_response"
[ -n "$TOKEN" ] || fail "login: token was empty"
pass "login (valid credentials, token issued)"

# 4. Invalid login
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@local.tn","password":"wrong-password"}' \
  "$BASE/api/v1/auth/login")
[ "$status" = "400" ] || fail "invalid login: expected 400, got $status"
pass "invalid login rejected (400)"

# 5. Password reset request
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@local.tn"}' \
  "$BASE/api/v1/auth/forgot-password")
[ "$status" = "200" ] || fail "password reset request: expected 200, got $status"
pass "password reset request (200)"

# 6. Frontend-to-backend proxy (the login call above already proves this,
#    but check explicitly against a GET too)
status=$(curl -s $RESOLVE -H "Authorization: Bearer $TOKEN" -o /dev/null -w '%{http_code}' "$BASE/api/v1/users/me")
[ "$status" = "200" ] || fail "frontend-to-backend proxy (GET /users/me): expected 200, got $status"
pass "frontend-to-backend proxy (200)"

ADMIN_ID=$(curl -s $RESOLVE -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/users/me" | python -c "import json,sys; print(json.load(sys.stdin)['id'])")

# 7. Valid file upload + MinIO retrieval
python -c "
from PIL import Image, ImageDraw
img = Image.new('RGB', (200, 100), color='white')
d = ImageDraw.Draw(img)
d.text((10, 40), 'CD SMOKE TEST', fill='black')
img.save('$WORKDIR/upload-test.jpg', 'JPEG')
"
upload_response=$(curl -s $RESOLVE -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@$WORKDIR/upload-test.jpg;type=image/jpeg" \
  "$BASE/api/v1/users/$ADMIN_ID/profile-image")
FILE_ID=$(echo "$upload_response" | python -c "import json,sys; print(json.load(sys.stdin)['profileImageFileId'])") \
  || fail "upload: could not extract profileImageFileId: $upload_response"
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' "$BASE/api/v1/files/$FILE_ID/view")
[ "$status" = "200" ] || fail "MinIO retrieval via backend: expected 200, got $status"
pass "valid upload + MinIO retrieval (200)"

# 8. Invalid content rejection (wrong file type)
echo "not an image" > "$WORKDIR/notanimage.txt"
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@$WORKDIR/notanimage.txt;type=text/plain" \
  "$BASE/api/v1/users/$ADMIN_ID/profile-image")
[ "$status" = "400" ] || fail "invalid content rejection: expected 400, got $status"
pass "invalid content rejected (400)"

# 9. Oversized file rejection
python -c "
with open('$WORKDIR/huge.jpg', 'wb') as f:
    f.write(b'\xff\xd8\xff\xe0' + b'0' * (60*1024*1024))
"
status=$(curl -s $RESOLVE -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@$WORKDIR/huge.jpg;type=image/jpeg" \
  "$BASE/api/v1/users/$ADMIN_ID/profile-image")
[ "$status" = "400" ] || fail "oversized upload rejection: expected 400, got $status"
pass "oversized upload rejected (400)"

# 10. OCR health (through backend->ocr DNS, indirectly via a real extract call below)
# 11. Real OCR extraction
extract_response=$(curl -s $RESOLVE -X POST -H "Authorization: Bearer $TOKEN" \
  -F "files=@$WORKDIR/upload-test.jpg;type=image/jpeg" \
  "$BASE/api/v1/ocr/extract")
echo "$extract_response" | python -c "
import json, sys
data = json.load(sys.stdin)
assert isinstance(data, list) and len(data) == 1, 'expected a single-element list response'
assert data[0].get('success') is True, f'OCR did not report success: {data[0]}'
" || fail "OCR extraction did not return a successful real result: $extract_response"
pass "OCR health + real extraction (success)"

echo "ALL SMOKE TESTS PASSED"
