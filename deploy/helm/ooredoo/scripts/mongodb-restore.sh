#!/bin/sh
# Manual MongoDB restore helper — NEVER run automatically (no CronJob
# references this file; see OBSERVABILITY_BACKUP.md "MongoDB restore
# process" for why: a restore is destructive by nature and must always
# be a deliberate human action, not a schedule). Intended to run from an
# operator's own machine with kubectl access, or via `kubectl exec` into
# a throwaway debug pod in the target namespace — this script itself has
# no Kubernetes-specific logic, it only needs `mongorestore` and network
# access to the target Mongo.
#
# Usage:
#   MONGODB_URI="mongodb://user:pass@host:27017/db?authSource=admin" \
#     ./mongodb-restore.sh /path/to/mongodb-pfe-20260101-020000.archive.gz
#
# Safety:
#   - Refuses to run without an explicit --yes flag (or interactive "yes"
#     confirmation) — see the confirm() call below.
#   - Does NOT pass --drop to mongorestore by default (existing
#     collections are merged into, not wiped) — pass --drop explicitly as
#     a second CLI flag if you specifically want the target database
#     replaced wholesale. Read the target/environment twice before doing
#     that against anything that isn't a disposable verification target.
#   - Never embeds credentials — MONGODB_URI must already be set in the
#     environment (e.g. exported from a value you retrieved out-of-band,
#     never pasted into shell history).
set -eu

archive="${1:?Usage: MONGODB_URI=... ./mongodb-restore.sh <archive.gz> [--drop] [--yes]}"
drop_flag=""
auto_yes="false"

shift
for arg in "$@"; do
  case "$arg" in
    --drop) drop_flag="--drop" ;;
    --yes) auto_yes="true" ;;
    *) echo "Unknown flag: $arg" >&2; exit 1 ;;
  esac
done

: "${MONGODB_URI:?MONGODB_URI must be set (never embed it in this script)}"

if [ ! -f "$archive" ]; then
  echo "Archive not found: $archive" >&2
  exit 1
fi

echo "=========================================================="
echo " MongoDB restore"
echo " Archive : $archive"
echo " Target  : (URI set via MONGODB_URI, not printed — contains a password)"
echo " Mode    : $( [ -n "$drop_flag" ] && echo 'DROP existing collections first' || echo 'merge into existing data' )"
echo "=========================================================="
echo "Expected downtime: the application should be scaled to 0 replicas"
echo "(or at minimum, put in maintenance mode) before running this — a"
echo "restore racing live writes can leave the database inconsistent."
echo "See OBSERVABILITY_BACKUP.md 'MongoDB restore process' for the full"
echo "stop -> restore -> restart -> verify checklist."
echo

if [ "$auto_yes" != "true" ]; then
  printf 'Type "yes" to proceed with this restore: '
  read -r confirmation
  if [ "$confirmation" != "yes" ]; then
    echo "Aborted — no changes made."
    exit 1
  fi
fi

echo "Restoring..."
# shellcheck disable=SC2086 # $drop_flag is intentionally either empty or a single literal flag, not user-controlled free text
mongorestore --gzip --archive="$archive" --uri="$MONGODB_URI" $drop_flag

echo "Restore command completed. Now verify manually:"
echo "  mongosh \"\$MONGODB_URI\" --eval 'db.getCollectionNames()'"
echo "  mongosh \"\$MONGODB_URI\" --eval 'db.cases.countDocuments({})'"
echo "Then restart/rescale the application and confirm real traffic works"
echo "before considering this restore complete."
