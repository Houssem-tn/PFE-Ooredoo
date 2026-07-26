#!/bin/sh
# Builds a self-contained offline deployment package from a release
# manifest (see deploy/releases/schema/release.schema.json): pulls every
# image by digest, saves each as its own tar, checksums everything, and
# copies the exact Helm chart + a values template alongside. Run this on
# any machine with registry access and Docker — the output directory is
# what actually gets carried (USB/internal transfer/etc.) to the
# air-gapped enterprise machine. No secrets are ever written here (the
# values template below has empty secret fields, same convention as
# values.yaml itself).
#
# Usage:
#   ./export.sh /path/to/release-<id>.json /path/to/output-dir
#
# Requires: docker, jq, sha256sum, the chart at ../helm/ooredoo (relative
# to this script).
set -eu

MANIFEST="${1:?Usage: $0 <release-manifest.json> <output-dir>}"
OUTDIR="${2:?Usage: $0 <release-manifest.json> <output-dir>}"
SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd -W 2>/dev/null || cd -- "$(dirname -- "$0")" && pwd)
CHART_DIR="${SCRIPT_DIR}/../helm/ooredoo"

# Pinned, not configurable per-release — these three are infrastructure,
# not application code, and are expected to stay stable across app
# releases. Bump here (and re-export) only when deliberately upgrading
# one of them.
MONGO_IMAGE="mongo:7.0"
MINIO_IMAGE="minio/minio:RELEASE.2025-04-08T15-41-24Z"
MC_IMAGE="minio/mc:RELEASE.2025-04-08T15-39-49Z"

mkdir -p "$OUTDIR/images" "$OUTDIR/chart"

echo "=== Reading release manifest: $MANIFEST ==="
backend_ref=$(jq -r '.images.backend.repository + "@" + .images.backend.digest' "$MANIFEST")
frontend_ref=$(jq -r '.images.frontend.repository + "@" + .images.frontend.digest' "$MANIFEST")
ocr_ref=$(jq -r '.images.ocr.repository + "@" + .images.ocr.digest' "$MANIFEST")
release_id=$(jq -r '.releaseId' "$MANIFEST")

echo "Release: $release_id"
echo "  backend:  $backend_ref"
echo "  frontend: $frontend_ref"
echo "  ocr:      $ocr_ref"
echo "  mongo:    $MONGO_IMAGE (pinned)"
echo "  minio:    $MINIO_IMAGE (pinned)"
echo "  mc:       $MC_IMAGE (pinned)"

save_one() {
  name="$1"
  ref="$2"
  echo "--- $name: pulling $ref ---"
  docker pull "$ref"
  echo "--- $name: saving to $OUTDIR/images/${name}.tar ---"
  docker save "$ref" -o "$OUTDIR/images/${name}.tar"
}

save_one backend "$backend_ref"
save_one frontend "$frontend_ref"
save_one ocr "$ocr_ref"
save_one mongodb "$MONGO_IMAGE"
save_one minio "$MINIO_IMAGE"
save_one mc "$MC_IMAGE"

echo "=== Copying Helm chart ==="
cp -r "$CHART_DIR"/* "$OUTDIR/chart/"

echo "=== Copying release manifest ==="
cp "$MANIFEST" "$OUTDIR/release-manifest.json"

echo "=== Writing values template (no secrets) ==="
cat > "$OUTDIR/values-offline-template.yaml" <<EOF
# Fill in real secret values and the imported image references
# (image-digests.txt, written below) before deploying. Never commit this
# file once real secrets are filled in.
backend:
  image:
    repository: "$(echo "$backend_ref" | cut -d@ -f1)"
    digest: "$(echo "$backend_ref" | cut -d@ -f2)"
  secrets:
    jwtSecret: ""
frontend:
  image:
    repository: "$(echo "$frontend_ref" | cut -d@ -f1)"
    digest: "$(echo "$frontend_ref" | cut -d@ -f2)"
ocr:
  image:
    repository: "$(echo "$ocr_ref" | cut -d@ -f1)"
    digest: "$(echo "$ocr_ref" | cut -d@ -f2)"
mongodb:
  auth:
    password: ""
minio:
  auth:
    accessKey: ""
    secretKey: ""
EOF

echo "=== Computing checksums ==="
(cd "$OUTDIR" && find images chart release-manifest.json values-offline-template.yaml -type f -print0 | \
  xargs -0 sha256sum > CHECKSUMS.sha256)

echo "=== Recording image digests ==="
{
  echo "backend  $backend_ref"
  echo "frontend $frontend_ref"
  echo "ocr      $ocr_ref"
  echo "mongodb  $MONGO_IMAGE"
  echo "minio    $MINIO_IMAGE"
  echo "mc       $MC_IMAGE"
} > "$OUTDIR/image-digests.txt"

echo "=== Done. Package at: $OUTDIR ==="
echo "Transfer the whole directory, then run verify-checksums.sh on the"
echo "receiving machine before import.sh."
