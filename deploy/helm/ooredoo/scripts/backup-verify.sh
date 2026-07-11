#!/bin/sh
# Backup verification procedure — see OBSERVABILITY_BACKUP.md "Backup
# verification": a backup is not proven to work until a real restore has
# been tested. Run this against a disposable/non-production namespace
# only (it writes a probe document/object, triggers real backup Jobs,
# and creates temporary restore resources) — never point NAMESPACE at a
# real production namespace.
#
# Usage:
#   NAMESPACE=ooredoo-staging ./backup-verify.sh
#
# Requires: kubectl context already pointed at the right cluster.
set -eu

NAMESPACE="${NAMESPACE:?Set NAMESPACE to the (non-production) namespace to test against}"
RUN_ID="verify-$(date +%Y%m%d-%H%M%S)"
LOG_FILE="./backup-verify-results.log"

log() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*" | tee -a "$LOG_FILE"; }

log "=== Backup verification run ${RUN_ID} starting against namespace ${NAMESPACE} ==="

# 1. Create a MongoDB probe document via the running backend's own Mongo
#    credentials (read from the same Secret the app itself uses — never
#    printed).
log "Step 1: writing MongoDB probe document"
# shellcheck disable=SC2016 # intentional: the single-quoted body must NOT
# expand $MONGO_INITDB_ROOT_USERNAME/$MONGO_INITDB_ROOT_PASSWORD locally —
# those need to expand inside the remote pod's own shell. The two pieces
# that DO need local expansion ($RUN_ID, the kubectl get cm lookup) are
# deliberately spliced in via '"..."' string concatenation instead.
kubectl exec -n "$NAMESPACE" statefulset/mongodb -- sh -c '
  mongosh --quiet \
    --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase admin \
    --eval "db.getSiblingDB(\"'"$(kubectl get cm backend-config -n "$NAMESPACE" -o jsonpath='{.data.MONGODB_DATABASE}')"'\").backup_verify_probe.insertOne({runId: \"'"$RUN_ID"'\", at: new Date()})"
' >>"$LOG_FILE" 2>&1
log "Probe document written (runId=${RUN_ID})"

# 2. Trigger the real backup CronJobs on demand (does not wait for their
#    schedule).
log "Step 2: triggering mongodb-backup and minio-backup Jobs on demand"
kubectl create job -n "$NAMESPACE" "mongodb-backup-${RUN_ID}" --from=cronjob/mongodb-backup >>"$LOG_FILE" 2>&1
kubectl create job -n "$NAMESPACE" "minio-backup-${RUN_ID}" --from=cronjob/minio-backup >>"$LOG_FILE" 2>&1

log "Step 3: waiting for both backup Jobs to complete (timeout 10m each)"
kubectl wait -n "$NAMESPACE" --for=condition=complete "job/mongodb-backup-${RUN_ID}" --timeout=10m >>"$LOG_FILE" 2>&1
kubectl wait -n "$NAMESPACE" --for=condition=complete "job/minio-backup-${RUN_ID}" --timeout=10m >>"$LOG_FILE" 2>&1
log "Both backup Jobs completed successfully"

log "=== Automated portion done. Remaining steps are deliberately manual: ==="
log "  4. Use scripts/mongodb-restore.sh against a disposable Mongo instance"
log "     (never the namespace's own real mongodb) and confirm the"
log "     backup_verify_probe collection contains runId=${RUN_ID}."
log "  5. Use scripts/minio-restore.sh restore-to-temp against the object(s)"
log "     this run's minio-backup Job just produced, and confirm object"
log "     counts/hashes match."
log "  6. Record the result (pass/fail + date) below, then run cleanup:"
log "     kubectl delete job -n ${NAMESPACE} mongodb-backup-${RUN_ID} minio-backup-${RUN_ID}"
log "     kubectl exec -n ${NAMESPACE} statefulset/mongodb -- mongosh ... --eval 'db.backup_verify_probe.deleteMany({runId: \"${RUN_ID}\"})'"
log ""
log "RESULT: <fill in manually after completing steps 4-6> — runId=${RUN_ID}, date=$(date -u +%Y-%m-%d)"
log "=== Backup verification run ${RUN_ID} — automated steps finished ==="
