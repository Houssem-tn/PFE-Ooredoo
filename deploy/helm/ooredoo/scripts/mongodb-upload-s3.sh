#!/bin/sh
# "upload" container of the mongodb-backup CronJob, destination.type=s3
# path (see templates/cronjob-mongodb-backup.yaml and OBSERVABILITY_
# BACKUP.md "MongoDB backup strategy"). Never writes the backup to the
# mongodb-data PVC — reads only from the emptyDir the mongodump
# initContainer wrote to.
set -eu

mc alias set dest "${DEST_ENDPOINT}" "$DEST_ACCESS_KEY" "$DEST_SECRET_KEY"
mc cp /backup/*.archive.gz "dest/${DEST_BUCKET}/mongodb/"
echo "Upload complete."

echo "Pruning backups older than ${RETENTION_DAYS} days..."
# `mc find --older-than` matches by each object's actual mtime, so this
# works regardless of naming/folder scheme — see minio-backup-s3.sh's
# identical use of this pattern for the equivalent reasoning.
mc find "dest/${DEST_BUCKET}/mongodb/" --older-than "${RETENTION_DAYS}d" --exec "mc rm {}" || true

echo "mongodb-backup finished successfully."
