#!/bin/sh
# minio-backup CronJob, destination.type=pvc path — the staging-only
# fallback (see mongodb-upload-pvc.sh's identical reasoning). Writes to
# the dedicated backup-store PVC, never to minio-data.
set -eu

mc alias set src "http://minio:${MINIO_PORT}" "$SRC_ACCESS_KEY" "$SRC_SECRET_KEY"

ts=$(date +%Y%m%d-%H%M%S)
mkdir -p "/backup-store/minio/${ts}"
echo "Mirroring src/${MINIO_BUCKET} -> local PVC /backup-store/minio/${ts}/ (staging fallback)"
mc mirror --quiet "src/${MINIO_BUCKET}" "/backup-store/minio/${ts}/"
echo "Mirror complete."

echo "Pruning snapshots older than ${RETENTION_DAYS} days..."
# The minio/mc image has no coreutils (no `find`/`rm` binary — see the
# 2026-07-26 incident this replaced: the mirror always succeeded but the
# job still failed because this step used shell `find`, which doesn't
# exist in this image). `mc find`/`mc rm` operate on local filesystem
# paths directly with no alias needed, same as the sibling S3 script's
# `mc find ... --older-than` pattern.
mc find /backup-store/minio --older-than "${RETENTION_DAYS}d" --exec "mc rm --force {}" || true

echo "minio-backup finished successfully."
