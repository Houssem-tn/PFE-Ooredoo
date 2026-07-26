# Secret handling — audit, classification, rotation

Phase 5 deliverable. Covers every secret this system's CI/CD and staging
deployment touch: where each one lives, who/what can read it, and the
safe procedure to rotate it. No secret value appears anywhere in this
file, in git, in CI logs, or in any manifest — see "Verification" at the
bottom for how that was checked.

## 1. Inventory and classification

| Secret | Where it lives | Who/what reads it | Committed to git? | Printed anywhere? |
|---|---|---|---|---|
| `backend-secret` (jwt-secret, mail-username, mail-password) | K8s Secret, `ooredoo-staging` namespace | backend Pod env | No — rendered by Helm from `--set-string`/`existingSecret`, never a values file in git | No |
| `mongodb-secret` (mongodb-username, mongodb-password) | K8s Secret, `ooredoo-staging` | mongodb StatefulSet, backend, mongodb-backup CronJob | No | No |
| `minio-secret` (minio-access-key, minio-secret-key) | K8s Secret, `ooredoo-staging` | minio StatefulSet, backend, minio-init Job, minio-backup CronJob | No | No |
| `ghcr-secret` (dockerconfigjson) | K8s Secret, `ooredoo-staging`, type `kubernetes.io/dockerconfigjson` | kubelet, for `imagePullSecrets` on every Deployment | No — created directly via `kubectl create secret docker-registry` by the operator, out of band | No — see the two incidents below |
| GitHub Actions `secrets.GITHUB_TOKEN` | GitHub-managed, per-workflow-run | `docker/login-action`, `gh release create` in `release.yml`/`deploy-staging.yml` | N/A (not a repo secret, auto-issued) | No |
| Fine-grained PAT (`read:packages` only) used to mint `ghcr-secret` | Nowhere persistent — used once to build one `kubectl create secret` command, then discarded | Only the K8s image-pull path | No | No — see incident below |
| `gh` CLI's own OAuth token (used for `gh workflow run`, `gh api`) | Local `gh` CLI credential store on the operator's machine | Operator's own shell | No | No |

**Note on `ghcr-secret`'s scope**: it was deliberately created from a PAT
scoped to `Packages: Read-only` and nothing else — never a token with
`repo` (write) scope. See `[[project_phase5_cd_release_hardening]]` for
why: my own working `gh` token had `repo` scope, which would have been a
real privilege escalation if reused for the cluster's pull secret, so a
separate, narrower credential was requested instead.

## 2. Two incidents worth recording (both handled correctly, not violations)

1. **Runner registration token**: registering the self-hosted runner
   requires a one-time registration token embedded in `config.cmd`'s
   arguments. The command was correctly blocked by the environment's own
   safety classifier before I could run it with the literal token
   inlined. Resolution: the operator ran the exact command themselves;
   I never saw or handled the token value.
2. **GHCR pull secret creation**: `kubectl create secret docker-registry
   ghcr-secret --docker-password=<PAT>` was, for the same reason, blocked
   from being run with the real PAT embedded. Resolution: identical —
   the operator ran it directly; I verified the result afterward only via
   metadata (`kubectl get secret ghcr-secret -o jsonpath='{.type}'`,
   key *names* not values) and a real pull test (`kubectl run` against
   the actual private image, confirmed via `kubectl get events` showing
   a successful 258 MB pull — not by trusting a command's own claimed
   exit status).

Neither the runner token nor the PAT was ever printed, logged, committed,
or passed through a tool call that would have exposed it in this
transcript.

## 3. `existingSecret` — the External Secrets Operator / SOPS-age interface

`backend.secrets.existingSecret`, `mongodb.auth.existingSecret`, and
`minio.auth.existingSecret` already exist in `values.yaml` (pre-Phase 5)
specifically so this chart never has to be the thing that generates a
real secret in an environment with a proper secret manager. Set the
value to the name of a Secret created by whatever tool you use — Sealed
Secrets, External Secrets Operator (ESO), SOPS+age decrypted via a
`kustomize`/Flux step, or plain `kubectl create secret` — and
`templates/secrets.yaml` skips rendering its own Secret for that
component entirely (`{{- if not .Values.X.existingSecret }}`).

**This phase does not install ESO or any other secret manager** — no
justification was presented for adding a new cluster-wide controller
just to prove the interface works, and the interface already works
today (it's exercised, in effect, every time `--set-string` is used
instead of a plaintext values file). If/when production adopts a real
secret manager, the integration point is: point `existingSecret` at the
Secret name that manager already produces, with the same keys
(`jwt-secret`/`mongodb-username`+`mongodb-password`/`minio-access-key`+`minio-secret-key`).
No chart change needed.

## 4. Rotation procedures

None of the following was executed for real this phase — rotating a
live credential "to prove it can be done" would itself be a change to
production-adjacent state with no corresponding need, which is exactly
what section 18's "do not rotate live Mongo/MinIO credentials merely to
prove rotation" rule forbids. What follows is the procedure to use when
rotation is actually needed.

### 4.1 MongoDB credential rotation

**Do not** simply edit `mongodb-secret` and restart pods — MongoDB
itself still has the *old* credential in its own `admin.system.users`
collection; changing only the K8s Secret makes the backend fail to
authenticate (new password, old database state) without rotating
anything real. The database credential must change *first*, in MongoDB
itself:

1. `kubectl exec -n ooredoo-staging mongodb-0 -- mongosh --username
   <existing-admin> --password <existing-password> --authenticationDatabase
   admin --eval 'db.getSiblingDB("admin").changeUserPassword("<user>",
   "<new-password>")'` — an authenticated, in-database operation, not a
   Secret edit.
2. Only after step 1 succeeds: update the `mongodb-secret` Secret's
   `mongodb-password` key (via `kubectl create secret ... --dry-run=client
   -o yaml | kubectl apply -f -`, or your secret manager's own rotation
   flow if using `existingSecret`) to match.
3. Restart the backend Deployment (`kubectl rollout restart
   deployment/backend -n ooredoo-staging`) so the new pods pick up the
   updated Secret via env var — existing pods keep the old value in
   their already-injected environment until restarted.
4. Verify: a fresh backend pod successfully authenticates (check
   `/actuator/health` and a real login) before considering rotation
   complete.

**Explicitly not a rotation method**: deleting `mongodb-data-mongodb-0`
(the PVC) and letting MongoDB re-initialize with fresh credentials from
the Secret. That destroys every real document in the database — it is
data loss, not credential rotation, and is exactly the action this
phase's safety rules prohibit once real data exists.

### 4.2 MinIO credential rotation

MinIO supports proper credential rotation without downtime via its own
admin API/`mc admin user` commands (or IAM service accounts, if adopted
later) — the same "change in the service itself first" principle as
Mongo:

1. `mc alias set staging http://minio:9000 <existing-access-key>
   <existing-secret-key>` from a pod/port-forward with network access.
2. `mc admin user add staging <new-access-key> <new-secret-key>` (or
   `mc admin user svcacct add` for a scoped service-account credential
   instead of rotating the root user directly — preferred where
   possible).
3. Grant the new credential the same policy the old one had, verify it
   can list/read/write the `pfe-files` bucket.
4. Update `minio-secret`'s keys to the new credential, restart backend
   (same as Mongo step 3).
5. Only after the application is confirmed working on the new
   credential: disable/remove the old one (`mc admin user remove` or
   `mc admin user disable`, not before).

**Explicitly not a rotation method**: deleting `minio-data-minio-0` and
re-initializing MinIO with new root credentials from the Secret. Same
reasoning as Mongo — destroys every real object once data exists.

### 4.3 `ghcr-secret` (image pull credential) rotation

Lower-stakes than the two above — it's read-only and holds no
application data. To rotate: mint a new fine-grained PAT with the same
minimal `Packages: Read-only` scope, then run
`kubectl create secret docker-registry ghcr-secret --docker-server=ghcr.io
--docker-username=<user> --docker-password=<new-token>
--dry-run=client -o yaml | kubectl apply -f -` (an update-in-place, no
pod restart required — kubelet re-reads the Secret at next pull), then
revoke the old PAT on GitHub. As with the initial creation, this command
embeds a real token and should be run directly by a human, not
constructed by an agent with the literal value inlined.

## 5. Staging vs. production secret separation

Only one environment (`staging`) exists as a live target this phase.
The GitHub Environment `staging` (see `deploy/RELEASE.md` §"GitHub
Environments") scopes its secrets separately from any future
`production` environment — GitHub Environments have independent secret
stores by design, so creating a `production` environment later
automatically gets a clean, non-overlapping secret namespace with no
extra work.

## 6. Verification performed

- `deploy/ci-scripts/validate-release-manifest.py`'s `check_no_secrets()`
  heuristically scans every release manifest for credential-shaped field
  names/values before it's ever uploaded as an artifact or release asset.
- Gitleaks (`gitleaks-action` in `ci.yml`) scans every push; the only
  findings this phase were narrowly-scoped false positives from
  deliberately-named CI-only dummy values (see `.gitleaksignore`'s own
  comments) — never a real credential.
- `kubectl get secret ... -o jsonpath='{.type}'`/key names were used to
  confirm Secret *shape* throughout this phase; `.data`/`stringData`
  values were never retrieved into a tool call or printed.
