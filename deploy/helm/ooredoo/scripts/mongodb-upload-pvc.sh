#!/bin/sh
# "upload" container of the mongodb-backup CronJob, destination.type=pvc
# path — the staging-only fallback when no external S3-compatible target
# is available yet (see values.yaml's backup.destination comment and
# OBSERVABILITY_BACKUP.md "Backup destinations"). Writes to a dedicated
# backup-store PVC, never to mongodb-data.
set -eu

cp /backup/*.archive.gz /backup-store/
echo "Copied to backup-store PVC (staging fallback)."

find /backup-store -name 'mongodb-*.archive.gz' -mtime +"${RETENTION_DAYS}" -delete

echo "mongodb-backup finished successfully."
