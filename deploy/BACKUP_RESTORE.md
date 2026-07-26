# Backup and restore hardening (Phase 5)

## 1. What already existed (pre-Phase 5)

The chart already shipped a complete backup design (see
`templates/cronjob-mongodb-backup.yaml`, `templates/cronjob-minio-backup.yaml`,
`scripts/mongodb-dump.sh`, `scripts/mongodb-upload-{s3,pvc}.sh`,
`scripts/minio-backup-{s3,pvc}.sh`, `scripts/mongodb-restore.sh`,
`scripts/minio-restore.sh`, `scripts/backup-verify.sh`):

- Two daily CronJobs (`mongodb-backup` 02:00, `minio-backup` 02:30),
  `concurrencyPolicy: Forbid` (a still-running run blocks the next
  schedule rather than queuing), `backoffLimit: 2`.
- Two destination modes: `s3` (external, preferred — the default) or
  `pvc` (staging-only fallback, writing to a dedicated `backup-store`
  PVC, never to `mongodb-data`/`minio-data`).
- MongoDB: `mongodump --archive --gzip` into a shared `emptyDir`, then a
  second container uploads/prunes by real file mtime (`RETENTION_DAYS`).
- MinIO: `mc mirror` into a fresh timestamped prefix per run (so each run
  is its own point-in-time snapshot, not a continuously-overwritten
  mirror), then prunes by object mtime.
- `backup-verify.sh`: writes a probe document, triggers both CronJobs
  on-demand, waits for completion, then hands off to the two restore
  scripts for a real, manual, disposable-target verification — by
  design, never automatic (a restore must always be a deliberate human
  action).

None of this was enabled on the live staging release before Phase 5
(`backup.enabled: false` in the deployed values) — Phase 4 never turned
it on.

## 2. What Phase 5 did

### 2.1 Enabled backup on live staging

```
helm upgrade ooredoo-staging . -n ooredoo-staging --reuse-values \
  --set backup.enabled=true \
  --set backup.destination.type=pvc \
  --set backup.destination.pvc.enabled=true \
  --set backup.mongodb.enabled=true \
  --set backup.minio.enabled=true
```

`type=pvc` (the staging fallback), not `s3`, because no external
S3-compatible endpoint exists for this local k3d environment — exactly
the case `values.yaml`'s own comment anticipates. This is purely
additive: a new `backup-store` PVC (20Gi, `local-path`) and two new
CronJobs. No existing Deployment/StatefulSet/PVC was touched.

**Real defect found and fixed in the process**: the first attempt at
this upgrade left the Helm release in `failed` status because
`backup-store`'s `local-path` StorageClass is `WaitForFirstConsumer` —
it only binds once something actually mounts it, which nothing had yet
(the CronJobs hadn't fired). Triggering an on-demand Job resolved the
binding. Separately, the `minio-backup` CronJob's on-demand Job runs
kept failing even though the actual `mc mirror` step visibly succeeded
each time (new timestamped snapshot directories were being created) —
root cause: `scripts/minio-backup-pvc.sh`'s retention step called plain
shell `find`, but the `minio/mc` image ships no coreutils at all (no
`find`, no `tar` — confirmed separately when `kubectl cp` into a bare
`minio/minio` pod failed with `tar: executable file not found`). Fixed
by replacing the shell `find`/`rm -rf` with `mc find ... --exec "mc rm
--force {}"`, the same primitive the sibling S3 script
(`minio-backup-s3.sh`) already used successfully — `mc` accepts local
filesystem paths directly, no alias needed. Re-verified with a real
on-demand Job after the fix: `succeeded: 1`, zero failures.

A `--reuse-values` gotcha surfaced during this: `helm upgrade
--reuse-values` reuses the values from the **last release recorded**,
which after a `failed` upgrade is *not* the values that failed — the
`backup.*` overrides had to be re-supplied explicitly on the next
`helm upgrade` rather than assumed to carry forward. Live staging is
now at Helm revision 17, `deployed`, with backup genuinely enabled and
working.

### 2.2 Created real logical backups

Triggered both CronJobs on-demand against the live staging data:

- MongoDB: `mongodump` archive
  (`mongodb-pfe-20260726-155124.archive.gz`, sha256
  `5c3b3c3c25e3a3a2cbcb1337d677921d7f76e024c413b9213849fdefef5a7bbf`)
  containing the real `pfe` database — 5 users, 8 notifications, 2
  file_metadata records, plus 8 other collections with 0 documents each
  at the time of this run.
- MinIO: a timestamped mirror
  (`minio/20260726-155208/user/.../upload-test.jpg`, sha256
  `b430c2dbae37d2eac82c364dacba2346e9fbac9deb142101c8ff8e8522927d03`)
  of the real `pfe-files` bucket.

Both stored on the dedicated `backup-store` PVC — outside
`mongodb-data`/`minio-data`, per the design's own invariant.

### 2.3 Isolated restore test (the part that actually proves a backup works)

A backup nobody has restored is not a proven backup. Performed a real
restore into fully disposable resources, never touching live staging
data:

1. Copied the archive + minio snapshot out of `backup-store` (via a
   short-lived, read-only-mounted inspection pod in `ooredoo-staging`)
   and re-verified sha256 matched at every hop.
2. Created a **new namespace**, `ooredoo-restore-test`, with two bare,
   disposable pods — `restore-mongo` (`mongo:7.0`) and `restore-minio`
   (`minio/minio`) — neither backed by a PVC (ephemeral pod storage
   only), so there was nothing shared with the real `mongodb-data`/
   `minio-data` volumes at any layer.
3. `mongorestore` into `restore-mongo`: **15/15 documents restored, 0
   failures** — exact match to the source archive's contents.
4. Compared live staging's real `users` collection count (5) against the
   restored copy's count (5) — confirmed identical, and confirmed the
   *live* collection was queried both before and after this test with no
   change, proving the restore never touched production-adjacent data.
5. `mc mirror` the MinIO snapshot into a temp bucket on `restore-minio`
   (via a `mc`-image pod already mounting `backup-store`, reaching
   `restore-minio` over its own temporary in-namespace Service — no
   `kubectl cp` needed for the object data itself, only for the initial
   extraction from the PVC). Verified the restored object's name, size,
   and — via the checksums recorded in step 1 — content match.

**Result: PASS.** Record counts and checksums matched exactly; live
staging data was unaffected throughout (confirmed by direct query
before/after, not assumed).

The `ooredoo-restore-test` namespace and its two pods were **left in
place** rather than deleted — per this phase's explicit rule to request
approval before deleting any temporary restore-test namespace/PVC, that
approval was asked for and the operator chose to leave it for now
rather than delete immediately. It holds no PVCs, so it costs nothing
beyond the two running pods; delete with:

```
kubectl delete namespace ooredoo-restore-test
```

whenever the operator confirms it's no longer needed.

## 3. Retention

`backup.mongodb.retentionDays` / `backup.minio.retentionDays` both
default to 7 days, pruned by each backup script's own retention step
(now working correctly for both destinations after the fix above).
`successfulJobsHistoryLimit`/`failedJobsHistoryLimit` on both CronJobs
are 3 — Job objects (not their backup payloads) beyond that are pruned
by Kubernetes itself.

## 4. What was deliberately not done

- No credential rotation (see `SECRETS.md` §4) — restoring/backing up
  does not require it, and rotating "to test the backup" would be
  rotation without cause.
- No S3-backed backup destination — this environment has no external
  S3-compatible endpoint to point at; the `s3` path in the chart is
  implemented and lint-checked (Phase 3's validation matrix includes a
  `backup-s3` values render) but not exercised against a real bucket
  this phase.
- No deletion of the `ooredoo-restore-test` namespace — awaiting a
  future go-ahead (see §2.3).
