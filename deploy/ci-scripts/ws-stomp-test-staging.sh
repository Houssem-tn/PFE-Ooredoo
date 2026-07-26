#!/bin/sh
# WebSocket/STOMP/SockJS verification through the real Traefik Ingress
# (never a port-forward) - see ws-stomp-ingress-client.mjs for the client
# itself. Reuses the Phase 4 seeded test admin (admin@local.tn); the
# disabled-user check flips that account's `enabled` flag temporarily and
# ALWAYS restores it in a trap, even on failure, so a broken run never
# leaves the staging test account locked out.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BASE="http://ooredoo.localhost:8081"
RESOLVE="--resolve ooredoo.localhost:8081:127.0.0.1"

fail() { echo "WS/STOMP TEST FAILED: $1" >&2; exit 1; }
pass() { echo "OK: $1"; }

set_admin_enabled() {
  value="$1"
  MONGO_USER=$(kubectl get secret mongodb-secret -n ooredoo-staging -o jsonpath='{.data.mongodb-username}' | base64 -d)
  MONGO_PASS=$(kubectl get secret mongodb-secret -n ooredoo-staging -o jsonpath='{.data.mongodb-password}' | base64 -d)
  kubectl exec -n ooredoo-staging mongodb-0 -- mongosh --quiet --username "$MONGO_USER" --password "$MONGO_PASS" \
    --authenticationDatabase admin --eval "db.getSiblingDB('pfe').users.updateOne({email:'admin@local.tn'},{\$set:{enabled:$value}})" >/dev/null
  unset MONGO_USER MONGO_PASS
}

# Guarantees the test account ends up enabled even if a check below fails.
trap 'set_admin_enabled true 2>/dev/null || true' EXIT

# 1. SockJS /info handshake
sockjs=$(curl -s $RESOLVE "$BASE/ws-notifications-sockjs/info")
echo "$sockjs" | python -c "import json,sys; d=json.load(sys.stdin); assert d.get('websocket') is True" \
  || fail "SockJS /info did not report websocket:true: $sockjs"
pass "SockJS /info (websocket:true)"

TOKEN=$(curl -s $RESOLVE -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@local.tn","password":"admin123"}' \
  "$BASE/api/v1/auth/login" | python -c "import json,sys; print(json.load(sys.stdin)['token'])")
[ -n "$TOKEN" ] || fail "could not obtain a token for STOMP tests"

# 2. Valid token -> CONNECTED
result=$(node "$SCRIPT_DIR/ws-stomp-ingress-client.mjs" "//ws-notifications" "$TOKEN" "valid-token")
echo "$result"
echo "$result" | grep -q "CONNECTED" || fail "valid token did not get CONNECTED: $result"
pass "STOMP valid token -> CONNECTED"

# 3. Missing token -> ERROR
result=$(node "$SCRIPT_DIR/ws-stomp-ingress-client.mjs" "//ws-notifications" "" "missing-token")
echo "$result"
echo "$result" | grep -q "ERROR frame received" || fail "missing token was not rejected: $result"
pass "STOMP missing token -> rejected"

# 4. Invalid token -> ERROR
result=$(node "$SCRIPT_DIR/ws-stomp-ingress-client.mjs" "//ws-notifications" "invalid.token.value" "invalid-token")
echo "$result"
echo "$result" | grep -q "ERROR frame received" || fail "invalid token was not rejected: $result"
pass "STOMP invalid token -> rejected"

# 5. Disabled user, otherwise-valid (pre-issued) token -> ERROR
DISABLED_TEST_TOKEN=$(curl -s $RESOLVE -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@local.tn","password":"admin123"}' \
  "$BASE/api/v1/auth/login" | python -c "import json,sys; print(json.load(sys.stdin)['token'])")
set_admin_enabled false
result=$(node "$SCRIPT_DIR/ws-stomp-ingress-client.mjs" "//ws-notifications" "$DISABLED_TEST_TOKEN" "disabled-user")
set_admin_enabled true
echo "$result"
echo "$result" | grep -q "ERROR frame received" || fail "disabled user with a pre-issued token was not rejected: $result"
pass "STOMP disabled-user token -> rejected"

# 6. Real live notification delivered over an Ingress-routed STOMP subscription
TOKEN=$(curl -s $RESOLVE -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@local.tn","password":"admin123"}' \
  "$BASE/api/v1/auth/login" | python -c "import json,sys; print(json.load(sys.stdin)['token'])")
node "$SCRIPT_DIR/ws-stomp-ingress-client.mjs" "//ws-notifications" "$TOKEN" "notif" "--subscribe-notif" > /tmp/notif-result.log 2>&1 &
NODE_PID=$!
sleep 3
suffix=$(date +%s)
curl -s $RESOLVE -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"cd-smoke-stt-$suffix@example.com\",\"password\":\"testpass123\",\"fullName\":\"CD Smoke STT $suffix\",\"role\":\"ROLE_STT\"}" \
  "$BASE/api/v1/auth/register" > /dev/null
wait $NODE_PID || true
cat /tmp/notif-result.log
grep -q "RECEIVED real push message" /tmp/notif-result.log || fail "no real notification was received over the Ingress STOMP subscription"
pass "real notification delivered over Ingress WebSocket"

echo "ALL WS/STOMP/SOCKJS TESTS PASSED"
