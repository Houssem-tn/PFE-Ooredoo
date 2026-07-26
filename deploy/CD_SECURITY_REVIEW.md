# CI/CD security review (Phase 5, section 23)

Reviewed every workflow in the backend repo (`ci.yml`, `integration.yml`,
`k8s-validate.yml`, and the three new `release.yml`/`deploy-staging.yml`/
`rollback-staging.yml`) against the checklist below. Frontend/OCR `ci.yml`
were reviewed for the workflow_dispatch-publish extension added this
phase specifically (same pattern, same reasoning) but not re-audited
line-by-line beyond that, since they're smaller and pre-existing.

## 1. Least-privilege permissions

**Status: pass.** Every workflow sets `permissions:` explicitly at the
top level (`contents: read` as the floor) and elevates only at the
specific job that needs more:
- `ci.yml`'s `gitleaks`/`docker` jobs get `contents: write`/`packages:
  write` only where they actually push a tag or an image.
- `release.yml`'s `release` job gets `contents: write` (for the optional
  GitHub Release) + `packages: read` (digest verification) — never
  `packages: write` (it never builds/pushes an image itself).
- `deploy-staging.yml`'s `deploy` job: `contents: read` only — it never
  writes to the repo.
- `rollback-staging.yml`: `contents: read` only.

No workflow uses the default (unscoped) `GITHUB_TOKEN` permissions.

## 2. Action pinning

**Status: pass, tag-pinned (not SHA-pinned).** Every `uses:` reference
across all workflows pins to a version tag (`@v4`, `@v5`, `@v6`, etc.) —
none are unpinned (`@main`/`@master`) and none are pinned to a mutable
floating major-only tag missing entirely. **Accepted exception**: this is
tag-pinning, not full-SHA-pinning — a compromised upstream action could
still retag a version after the fact. Full SHA-pinning
(`actions/checkout@<40-char-sha>`) is stricter but was not adopted this
phase; flagged as a future hardening step, not blocking, since all
actions used are from verified publishers (`actions/*`, `docker/*`,
`github/*`) with no history of this class of compromise.

## 3. Untrusted-input injection / command injection

**Status: fixed this phase.** The first draft of all three new workflows
interpolated `${{ inputs.* }}` directly into `run:` script bodies — a
known GitHub Actions anti-pattern, since `${{ }}` expressions are
substituted as raw text *before* the shell runs, so a value containing
shell metacharacters could break out of quoting even though every input
here is `workflow_dispatch`-only (i.e., only a repo collaborator can
supply it — this is defense in depth, not a response to an actual
untrusted-input path). Fixed by routing every input through a job-level
`env:` block and referencing `$VARNAME` inside `run:` instead — env vars
go through the runner's own environment mechanism, not text
substitution. Verified via `grep -n '\${{ inputs\.'` across all three
files: only `env:` key *definitions* and one `with:` block
(`actions/upload-artifact@v4`'s `name:`/`path:`, which is an action
input, not a shell string) remain.

`ci.yml`/`integration.yml`/`k8s-validate.yml` (pre-existing, not modified
for this beyond the digest-recording addition) were spot-checked for the
same pattern — none found; they don't take `workflow_dispatch` string
inputs at all outside this phase's own addition.

## 4. Artifact poisoning

**Status: pass.** `release.yml` uploads the release manifest as an
artifact/release asset — nothing downstream trusts an artifact produced
by an untrusted actor (there's no PR-triggered job that produces an
artifact later jobs consume). `deploy-staging.yml` downloads the release
manifest via `gh release download`, then immediately re-validates it
against the JSON schema and re-verifies every image digest against GHCR
directly — it never trusts the manifest's own claims about a digest
without independently confirming that digest is real and pullable.

## 5. Unsafe checkout

**Status: pass.** Every `actions/checkout@v4` call uses `sparse-checkout`
scoped to only the paths that job actually needs (e.g. `release.yml`
checks out only `deploy/releases/schema`, `deploy/ci-scripts`,
`deploy/helm/ooredoo/Chart.yaml` — never the full repo). No workflow
checks out a PR head ref from a fork with elevated permissions (no
`pull_request_target` anywhere in this repo's workflows — confirmed via
`grep -rn pull_request_target .github/workflows/`, zero matches).

## 6. Secret exposure

**Status: pass.** No workflow echoes/prints a secret value. `docker/
login-action@v3` receives `secrets.GITHUB_TOKEN` directly as a `with:`
input (GitHub's own recommended pattern — never routed through a shell
variable that could leak into logs). `deploy-staging.yml`'s real-secret
read step (`kubectl get secret ... -o jsonpath=...` into shell vars for
the `helm upgrade` command) explicitly `unset`s those variables
immediately after use and never echoes them.

## 7. `pull_request_target` misuse

**Status: pass — not used anywhere.** See §5.

## 8. Self-hosted runner exposure

**Status: pass, reviewed against the operator's own 9-point checklist
before first use.** `deploy-staging.yml`/`rollback-staging.yml`'s
cluster-touching jobs:
- `runs-on: [self-hosted, ooredoo-staging]` — both labels required, not
  just `self-hosted` alone (which would let ANY self-hosted runner in
  the account/org pick up the job, not just this dedicated one).
- Triggered only by `workflow_dispatch` — confirmed via each workflow's
  own `on:` block; a `Confirm this is a trusted, non-PR trigger` step
  additionally asserts `github.event_name == 'workflow_dispatch'` at
  runtime as a second, defense-in-depth check.
- Never `pull_request`/`pull_request_target` — see §5, confirmed absent
  repo-wide.
- Asserts `kubectl config current-context == k3d-ooredoo-staging` and
  that the `ooredoo-staging` namespace exists, before touching anything
  — a workflow accidentally pointed at the wrong kubeconfig/cluster
  fails immediately instead of silently acting on the wrong target.
- Never deletes a namespace, PVC, or Helm release (grep confirms no
  `kubectl delete namespace\|pvc\|helm uninstall\|--force` anywhere in
  either file).

## 9. Excessive token scope

**Status: pass** — see §1. The GHCR pull secret used by the *cluster*
(not by CI) is a separately-issued fine-grained PAT scoped to `Packages:
Read-only` only (see `SECRETS.md` §1) — never the CI workflow's own
`GITHUB_TOKEN`, and never a token with `repo` write scope.

## 10. Unbounded execution / missing timeout / concurrency

**Status: pass for all three new workflows** (`release.yml`: 15 min,
`deploy-staging.yml`: 10 min validate + 30 min deploy, `rollback-
staging.yml`: 20 min — all with `concurrency:` groups preventing
overlapping runs; `deploy-staging.yml` and `rollback-staging.yml`
deliberately share the same concurrency group, `ooredoo-staging-
deployment`, so a rollback and a forward deployment can never run
against the release simultaneously).

**Accepted pre-existing gap, not introduced this phase**: `ci.yml`,
`integration.yml`, and `k8s-validate.yml` have no explicit
`timeout-minutes` at all (falling back to GitHub's default 360-minute
job timeout). Concurrency groups are present on all three
(`backend-ci-${{ github.ref }}`, `integration-${{ github.ref }}`,
`k8s-validate-${{ github.ref }}`), so the missing-timeout gap is bounded
in practice by "the next push cancels the previous run" rather than by
an explicit ceiling. Flagged as a low-priority future improvement, not
fixed here — these files predate Phase 5 and changing them was outside
this phase's scope beyond the specific additions already made
(workflow_dispatch publish gate, digest recording).

## 11. Mutable action dependencies

**Status: pass** — see §2 (tag-pinned, not floating branch refs).

## 12. Unsafe shell interpolation

**Status: fixed this phase** — see §3. No remaining `${{ }}`
interpolation inside any `run:` body across the three new workflows;
pre-existing workflows spot-checked and clean.

## Summary

No open findings requiring immediate action. Two accepted, explicitly
documented exceptions: tag-pinning instead of full SHA-pinning (§2), and
missing explicit `timeout-minutes` on three pre-existing workflows (§10)
— both low-severity, both flagged for future hardening rather than
fixed reflexively in a phase that wasn't scoped to touch those files
beyond their specific additions.
