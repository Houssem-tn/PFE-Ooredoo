#!/bin/sh
# Run on the receiving (enterprise/air-gapped) machine, right after
# transferring the package directory produced by export.sh and BEFORE
# importing anything — never trust a transferred artifact you haven't
# checksummed.
set -eu

PKG_DIR="${1:?Usage: $0 <package-dir>}"
cd "$PKG_DIR"

echo "=== Verifying CHECKSUMS.sha256 ==="
sha256sum -c CHECKSUMS.sha256

echo "=== All checksums verified OK ==="
