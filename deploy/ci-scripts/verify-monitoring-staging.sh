#!/bin/sh
# Verifies Prometheus scrape targets and the Grafana datasource are
# healthy after a staging deployment. Prometheus/Grafana are not exposed
# through the application Ingress (see OBSERVABILITY_BACKUP.md "Monitoring
# security" - by design, this chart never routes to them) - a short-lived
# kubectl port-forward is the correct, and only, way to reach them, unlike
# the application's own WebSocket/STOMP tests which must go through the
# real Ingress.
set -eu

fail() { echo "MONITORING VERIFY FAILED: $1" >&2; exit 1; }
pass() { echo "OK: $1"; }

cleanup() {
  [ -n "${PROM_PF_PID:-}" ] && kill "$PROM_PF_PID" 2>/dev/null || true
}
trap cleanup EXIT

kubectl port-forward -n monitoring svc/prometheus-operated 19091:9090 >/tmp/prom-pf-verify.log 2>&1 &
PROM_PF_PID=$!
sleep 4

targets=$(curl -s "http://localhost:19091/api/v1/targets")
for pool in "serviceMonitor/ooredoo-staging/backend/0" "serviceMonitor/ooredoo-staging/ocr/0"; do
  health=$(echo "$targets" | python -c "
import json, sys
d = json.load(sys.stdin)
for t in d['data']['activeTargets']:
    if t['scrapePool'] == '$pool':
        print(t['health'])
        break
else:
    print('missing')
")
  [ "$health" = "up" ] || fail "$pool target health is '$health', expected 'up'"
  pass "$pool is up"
done

rules=$(curl -s "http://localhost:19091/api/v1/rules")
echo "$rules" | python -c "
import json, sys
d = json.load(sys.stdin)
groups = [g for g in d['data']['groups'] if g['name'].startswith('ooredoo.')]
assert groups, 'no ooredoo.* alert rule groups loaded'
for g in groups:
    errored = [r for r in g['rules'] if r.get('lastError')]
    assert not errored, f'group {g[\"name\"]} has rules with evaluation errors: {errored}'
print(f'{len(groups)} ooredoo.* alert groups loaded, 0 evaluation errors')
" || fail "PrometheusRule groups did not load cleanly"
pass "PrometheusRule alert groups load without evaluation errors"

echo "ALL MONITORING CHECKS PASSED"
