#!/bin/sh
# minio-backup CronJob, destination.type=s3 path (see templates/cronjob-
# minio-backup.yaml and OBSERVABILITY_BACKUP.md "MinIO backup strategy").
# Mirrors into a fresh timestamped prefix every run instead of one
# continuously-overwritten mirror, so each run is its own point-in-time
# snapshot even without native object versioning on the destination.
# Never treats the source minio-data PVC as its own backup.
set -eu

mc alias set src "http://minio:${MINIO_PORT}" "$SRC_ACCESS_KEY" "$SRC_SECRET_KEY"
mc alias set dest "${DEST_ENDPOINT}" "$DEST_ACCESS_KEY" "$DEST_SECRET_KEY"

ts=$(date +%Y%m%d-%H%M%S)
echo "Mirroring src/${MINIO_BUCKET} -> dest/${DEST_BUCKET}/minio/${ts}/"
mc mirror --quiet "src/${MINIO_BUCKET}" "dest/${DEST_BUCKET}/minio/${ts}/"
echo "Mirror complete."

echo "Pruning objects older than ${RETENTION_DAYS} days..."
# Matches by each object's actual mtime, not by parsing the ${ts} prefix
# — correctly prunes old snapshots regardless of folder naming.
mc find "dest/${DEST_BUCKET}/minio/" --older-than "${RETENTION_DAYS}d" --exec "mc rm {}" || true

echo "minio-backup finished successfully."
