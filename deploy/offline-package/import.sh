#!/bin/sh
# Loads every image tar into the target K3s node's containerd image
# store directly (not via `docker load` + `k3d image import`, since the
# enterprise target is a real K3s cluster, not k3d) and prints the
# digest of each imported image so it can be compared against
# image-digests.txt (produced by export.sh) before deploying — an image
# that imports under a different digest than expected must not be used.
#
# Usage (run on a K3s node, or anywhere with `ctr` + access to K3s's
# containerd socket):
#   sudo ./import.sh /path/to/package-dir
#
# Requires: verify-checksums.sh to have already passed.
set -eu

PKG_DIR="${1:?Usage: $0 <package-dir>}"
CTR_ADDRESS="${CTR_ADDRESS:-/run/k3s/containerd/containerd.sock}"

for name in mongodb minio mc backend frontend ocr; do
  tar="$PKG_DIR/images/${name}.tar"
  [ -f "$tar" ] || { echo "::error::missing $tar" >&2; exit 1; }
  echo "=== Importing $name from $tar ==="
  ctr --address "$CTR_ADDRESS" -n k8s.io images import "$tar"
done

echo "=== Imported image digests (compare against image-digests.txt) ==="
ctr --address "$CTR_ADDRESS" -n k8s.io images list -q | grep -E 'backend|frontend|ocr|mongo|minio'

echo "=== Import complete. Next: fill in deploy.sh's values file with real secrets, then run deploy.sh ==="
