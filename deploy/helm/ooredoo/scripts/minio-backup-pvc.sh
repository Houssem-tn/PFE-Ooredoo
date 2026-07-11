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

find /backup-store/minio -mindepth 1 -maxdepth 1 -type d -mtime +"${RETENTION_DAYS}" -exec rm -rf {} +

echo "minio-backup finished successfully."
