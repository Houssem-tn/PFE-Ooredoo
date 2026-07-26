#!/bin/sh
# Same safety floor as .github/workflows/rollback-staging.yml, adapted
# for a disconnected/manual environment with no GitHub Actions available
# at all — rolls back the ooredoo Helm release and re-verifies PVCs stay
# Bound. See that workflow's own comment for why MIN_SAFE_REVISION
# exists: revisions before the Phase 4 volumeClaimTemplates
# immutable-label fix would fail the exact way that incident documented.
# Adjust MIN_SAFE_REVISION for the target environment's own release
# history before relying on this script there.
#
# Usage:
#   ./rollback.sh <namespace> <target-revision>
set -eu

NAMESPACE="${1:?Usage: $0 <namespace> <target-revision>}"
TARGET_REVISION="${2:?Usage: $0 <namespace> <target-revision>}"
MIN_SAFE_REVISION="${MIN_SAFE_REVISION:-1}"

if ! echo "$TARGET_REVISION" | grep -Eq '^[0-9]+$'; then
  echo "::error::target revision must be a positive integer" >&2
  exit 1
fi
if [ "$TARGET_REVISION" -lt "$MIN_SAFE_REVISION" ]; then
  echo "::error::revision $TARGET_REVISION is below MIN_SAFE_REVISION=$MIN_SAFE_REVISION for this environment" >&2
  exit 1
fi

helm history ooredoo -n "$NAMESPACE" -o json | \
  python3 -c "
import json, sys
revisions = {r['revision'] for r in json.load(sys.stdin)}
target = int('$TARGET_REVISION')
if target not in revisions:
    print(f'::error::revision {target} not found in history: {sorted(revisions)}', file=sys.stderr)
    sys.exit(1)
"

for pvc in mongodb-data-mongodb-0 minio-data-minio-0; do
  phase=$(kubectl get pvc "$pvc" -n "$NAMESPACE" -o jsonpath='{.status.phase}')
  [ "$phase" = "Bound" ] || { echo "::error::$pvc is not Bound, refusing to roll back" >&2; exit 1; }
done

helm rollback ooredoo "$TARGET_REVISION" -n "$NAMESPACE" --wait --timeout 10m

kubectl rollout status deployment/backend -n "$NAMESPACE" --timeout=300s
kubectl rollout status deployment/frontend -n "$NAMESPACE" --timeout=300s
kubectl rollout status deployment/ocr -n "$NAMESPACE" --timeout=300s
kubectl rollout status statefulset/mongodb -n "$NAMESPACE" --timeout=300s
kubectl rollout status statefulset/minio -n "$NAMESPACE" --timeout=300s

for pvc in mongodb-data-mongodb-0 minio-data-minio-0; do
  phase=$(kubectl get pvc "$pvc" -n "$NAMESPACE" -o jsonpath='{.status.phase}')
  [ "$phase" = "Bound" ] || { echo "::error::$pvc lost Bound status after rollback" >&2; exit 1; }
done

echo "=== Rollback to revision $TARGET_REVISION complete. Both PVCs remain Bound. ==="
echo "Run the application's own smoke tests before considering this done."
