#!/bin/sh
# initContainer of the mongodb-backup CronJob (templates/cronjob-mongodb-
# backup.yaml). Dumps into a shared emptyDir — the "upload" container
# that runs next reads from here, this script never touches the
# destination itself. The URI is built inline, never exported/echoed —
# see OBSERVABILITY_BACKUP.md "never prints credentials".
set -eu

ts=$(date +%Y%m%d-%H%M%S)
echo "Starting mongodump at ${ts}"

mongodump \
  --uri="mongodb://${MONGODB_USERNAME}:${MONGODB_PASSWORD}@mongodb:${MONGODB_PORT}/${MONGODB_DATABASE}?authSource=admin" \
  --gzip \
  --archive="/backup/mongodb-${MONGODB_DATABASE}-${ts}.archive.gz"

echo "mongodump complete:"
ls -la /backup
