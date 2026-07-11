#!/bin/sh
# Manual MinIO restore helper — NEVER run automatically. Restores into a
# TEMPORARY bucket first and requires a second, explicit invocation to
# promote into the real bucket — see OBSERVABILITY_BACKUP.md "MinIO
# restore process" for why: this script will never silently overwrite
# production objects.
#
# Usage (step 1 — always required):
#   SRC_ENDPOINT=https://s3.example.com SRC_ACCESS_KEY=... SRC_SECRET_KEY=... \
#   DEST_ENDPOINT=http://minio.ooredoo-staging.svc.cluster.local:9000 \
#   DEST_ACCESS_KEY=... DEST_SECRET_KEY=... \
#     ./minio-restore.sh restore-to-temp <backup-bucket>/minio/<timestamp>/ <temp-bucket-name>
#
# Usage (step 2 — only after you've verified the temp bucket manually):
#     ./minio-restore.sh promote <temp-bucket-name> <real-bucket-name> --yes
set -eu

action="${1:?Usage: $0 restore-to-temp <source-prefix> <temp-bucket> | promote <temp-bucket> <real-bucket> [--yes]}"

case "$action" in
  restore-to-temp)
    source_prefix="${2:?source prefix required, e.g. ooredoo-backups/minio/20260101-020000/}"
    temp_bucket="${3:?temp bucket name required}"
    : "${SRC_ENDPOINT:?}" "${SRC_ACCESS_KEY:?}" "${SRC_SECRET_KEY:?}"
    : "${DEST_ENDPOINT:?}" "${DEST_ACCESS_KEY:?}" "${DEST_SECRET_KEY:?}"

    mc alias set restsrc "$SRC_ENDPOINT" "$SRC_ACCESS_KEY" "$SRC_SECRET_KEY"
    mc alias set restdest "$DEST_ENDPOINT" "$DEST_ACCESS_KEY" "$DEST_SECRET_KEY"

    mc mb --ignore-existing "restdest/${temp_bucket}"
    echo "Copying restsrc/${source_prefix} -> restdest/${temp_bucket}/ ..."
    mc mirror --quiet "restsrc/${source_prefix}" "restdest/${temp_bucket}/"

    src_count=$(mc find "restsrc/${source_prefix}" --type f | wc -l)
    dest_count=$(mc find "restdest/${temp_bucket}" --type f | wc -l)
    echo "Source object count: ${src_count}"
    echo "Temp bucket object count: ${dest_count}"
    if [ "$src_count" != "$dest_count" ]; then
      echo "WARNING: counts do not match — investigate before promoting." >&2
    fi

    echo
    echo "Next: spot-check a few objects (hashes, sizes) between"
    echo "  restsrc/${source_prefix}  and  restdest/${temp_bucket}/"
    echo "e.g.: mc stat restsrc/${source_prefix}<object> ; mc stat restdest/${temp_bucket}/<object>"
    echo "Only after that, run:"
    echo "  $0 promote ${temp_bucket} <real-bucket> --yes"
    ;;

  promote)
    temp_bucket="${2:?temp bucket name required}"
    real_bucket="${3:?real (production) bucket name required}"
    yes_flag="${4:-}"
    : "${DEST_ENDPOINT:?}" "${DEST_ACCESS_KEY:?}" "${DEST_SECRET_KEY:?}"

    mc alias set restdest "$DEST_ENDPOINT" "$DEST_ACCESS_KEY" "$DEST_SECRET_KEY"

    echo "This will copy every object from '${temp_bucket}' into '${real_bucket}',"
    echo "OVERWRITING any object with the same key already there. This does NOT"
    echo "delete objects that exist in '${real_bucket}' but not in '${temp_bucket}'"
    echo "(mc cp, not mc mirror --overwrite --remove) — a deliberate choice so a"
    echo "partial/older backup can never delete newer production objects."
    if [ "$yes_flag" != "--yes" ]; then
      printf 'Type "yes" to promote into %s: ' "$real_bucket"
      read -r confirmation
      [ "$confirmation" = "yes" ] || { echo "Aborted — no changes made."; exit 1; }
    fi

    mc cp --recursive "restdest/${temp_bucket}/" "restdest/${real_bucket}/"
    echo "Promotion complete. Verify the application can read the restored objects."
    ;;

  *)
    echo "Unknown action: $action (expected restore-to-temp or promote)" >&2
    exit 1
    ;;
esac
