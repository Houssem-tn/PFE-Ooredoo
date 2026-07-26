#!/bin/sh
# Installs/upgrades the ooredoo release from the offline package's own
# bundled chart copy, using digest-pinned image references (never
# `:latest`, never a bare tag) — same `ooredoo.image` helper Phase 5
# added to the chart, so this is the exact same rendering logic already
# proven against the online staging cluster, just pointed at locally-
# imported images instead of a registry pull.
#
# Usage:
#   ./deploy.sh <package-dir> <namespace> <values-file-with-real-secrets>
#
# The values file is never bundled in the package itself (see
# export.sh's values-offline-template.yaml, which has empty secret
# fields) — copy that template, fill in real values out of band, and
# pass the filled-in copy here.
set -eu

PKG_DIR="${1:?Usage: $0 <package-dir> <namespace> <values-file>}"
NAMESPACE="${2:?Usage: $0 <package-dir> <namespace> <values-file>}"
VALUES_FILE="${3:?Usage: $0 <package-dir> <namespace> <values-file>}"

[ -f "$VALUES_FILE" ] || { echo "::error::values file not found: $VALUES_FILE" >&2; exit 1; }

# imagePullPolicy must be IfNotPresent (or Never) here — there is no
# registry to fall back to on an air-gapped cluster, and Always would
# make every pod restart fail once the locally-imported image isn't
# re-pullable.
helm upgrade --install ooredoo "$PKG_DIR/chart" \
  --namespace "$NAMESPACE" --create-namespace \
  -f "$VALUES_FILE" \
  --set backend.image.pullPolicy=IfNotPresent \
  --set frontend.image.pullPolicy=IfNotPresent \
  --set ocr.image.pullPolicy=IfNotPresent \
  --wait --timeout 10m

echo "=== Deployed. Verify with: ==="
echo "  kubectl get pods -n $NAMESPACE"
echo "  helm history ooredoo -n $NAMESPACE"
echo "Confirm every pod's actual image digest matches $PKG_DIR/image-digests.txt:"
echo "  kubectl get pods -n $NAMESPACE -o jsonpath='{range .items[*]}{.metadata.name}{\"\\t\"}{.status.containerStatuses[*].imageID}{\"\\n\"}{end}'"
