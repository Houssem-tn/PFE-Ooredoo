# Online and offline (enterprise) deployment

## 1. Online deployment (registry-connected, e.g. this phase's staging)

1. A release manifest exists (`deploy/releases/release-<id>.json`,
   schema-validated), naming exact image digests for backend/frontend/ocr
   — see `RELEASE.md` for how it's produced (`release.yml`).
2. `deploy-staging.yml` (self-hosted runner, `workflow_dispatch` only)
   downloads that manifest, re-verifies each digest is genuinely pullable
   from GHCR, renders the chart with `ooredoo.image` resolving each
   component to `repository@digest`, and `helm upgrade --install`s.
3. Kubernetes pulls images directly from GHCR using the namespace's
   `ghcr-secret` (`imagePullSecrets`) — proven this phase with a real
   258 MB private-image pull, confirmed via `kubectl get events`.
4. Post-deploy smoke/WS-STOMP/monitoring verification runs before the
   workflow is considered green.

This is the only mode a registry-connected environment (this staging
cluster, or a future connected production cluster) needs — the offline
path below exists specifically for a target with **no** route to GHCR
at all.

## 2. Offline (air-gapped enterprise) package

Scripts live in `deploy/offline-package/`. All are real, runnable
scripts, not pseudocode — checked into git, reviewed the same as any
other script in this repo. **Not executed against a real enterprise
machine this phase** (this phase's task explicitly says not to test on
the enterprise machine yet — no such machine's specs or access are known
yet). What follows is the reproducible procedure; each script's own
comments explain the safety reasoning.

### 2.1 Export (run where GHCR is reachable, e.g. the CI runner or an
operator's own machine)

```
./deploy/offline-package/export.sh deploy/releases/release-<id>.json /path/to/pkg
```

Produces, under `/path/to/pkg/`:

- `images/{backend,frontend,ocr,mongodb,minio,mc}.tar` — every image the
  full stack needs, including the two data stores and the `mc` tool the
  backup CronJobs use, saved by digest via `docker save`.
- `chart/` — a full copy of `deploy/helm/ooredoo` at the exact commit
  this release was cut from.
- `release-manifest.json` — copied in verbatim.
- `values-offline-template.yaml` — image repository/digest fields
  pre-filled, every secret field left empty (same convention as the
  chart's own `values.yaml` — never a real value, never a plausible
  placeholder that could pass validation unedited).
- `image-digests.txt` — the exact digest expected for each image, to
  diff against what actually gets imported on the receiving end.
- `CHECKSUMS.sha256` — sha256 of every file above.

### 2.2 Transfer

Whatever mechanism the enterprise's own policy requires (USB drive,
internal file transfer, physical media) — out of scope for this repo to
prescribe. The package directory is self-contained; nothing else is
needed.

### 2.3 Verify (on the receiving machine, before importing anything)

```
./deploy/offline-package/verify-checksums.sh /path/to/pkg
```

Fails loudly on any mismatch — never import a package that fails this
check.

### 2.4 Import (on a K3s node, or wherever `ctr` can reach that node's
containerd socket)

```
sudo ./deploy/offline-package/import.sh /path/to/pkg
```

Loads each tar directly into containerd's `k8s.io` namespace (not
`docker load`, since the target is K3s, not a Docker daemon) and prints
each image's resulting digest so it can be diffed against
`image-digests.txt` — an image that imports under an unexpected digest
must not be used.

### 2.5 Deploy

```
cp /path/to/pkg/values-offline-template.yaml /path/to/pkg/values-real.yaml
# edit values-real.yaml: fill in jwtSecret, mongodb password, minio keys
./deploy/offline-package/deploy.sh /path/to/pkg <namespace> /path/to/pkg/values-real.yaml
```

Uses `imagePullPolicy: IfNotPresent` (never `Always`) — there is no
registry to fall back to on a disconnected cluster.

### 2.6 Confirm digests

`deploy.sh`'s own final step prints each running pod's `imageID` for the
operator to diff against `image-digests.txt` by hand — the same
digest-verification discipline the online path enforces via
`deploy-staging.yml`'s own preflight, just done manually since there's
no CI runner on an air-gapped network to automate it.

### 2.7 Rollback

```
./deploy/offline-package/rollback.sh <namespace> <target-revision>
```

Same safety floor as `rollback-staging.yml` (revision existence check,
PVC-Bound check before and after, rollout verification) — set
`MIN_SAFE_REVISION` for that environment's own release history if it
has ever hit a schema-breaking chart change analogous to Phase 4's
volumeClaimTemplates incident.

## 3. What this does not do

- Does not itself perform the initial K3s installation, air-gapped
  container-registry mirror configuration, or OS-level hardening of the
  enterprise machine — those are infrastructure prerequisites, not
  something a Helm chart script should assume or automate blindly.
- Does not test any of §2 against a real enterprise target this phase.
