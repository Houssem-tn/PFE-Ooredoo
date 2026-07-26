# Branch protection — proposal (NOT applied)

Per this phase's explicit rule, no branch-protection setting was
changed. This document proposes exact rules for `develop` and `main` and
must be reviewed/approved before anyone applies them.

## 1. Current state

Audited via `gh api repos/Houssem-tn/PFE-Ooredoo/branches/{branch}/protection`
— **no branch protection is currently configured** on either `develop`
or `main` in this repository. Anyone with write access can currently
force-push or delete either branch, and no status check is required
before merge.

## 2. Why not apply this now

Applying required-status-checks to a repo with an existing stacked PR
chain (PR #17, `phase4/k3d-staging` → not yet merged; this phase's own
branch will open a PR targeting it) risks deadlocking that chain if the
required checks don't match what those PRs' branches can actually
produce (e.g. requiring `deploy-staging.yml` to pass on a PR branch that
can never reach the self-hosted runner because it isn't a trusted
trigger). Every rule below is designed to avoid that trap, but the task
explicitly asks for approval before any of it is applied — so this stays
a proposal.

## 3. Proposed rule set — `main`

- Require a pull request before merging (no direct pushes) — block
  `git push` directly to `main` entirely, including for maintainers,
  except through an explicitly-documented emergency-only bypass (out of
  scope to design here; GitHub supports "Do not allow bypassing the
  above settings" as an option to close even that gap, recommended once
  the team is comfortable).
- Require 1 approving review, dismiss stale approvals on new commits.
- Require conversations to be resolved before merging.
- Require branches to be up to date with `main` before merging.
- Require the following status checks (exact names, as they appear in
  GitHub's check list today):
  - `Environment validation` (backend `ci.yml`)
  - `Build, test, quality gates` (backend `ci.yml`)
  - `OWASP Dependency-Check` (backend `ci.yml`)
  - `Secret scan (Gitleaks)` (backend `ci.yml`)
  - `Docker build, scan, (push)` (backend `ci.yml`)
  - `Compose build, up, smoke tests` (backend `integration.yml` — only
    actually required when `INTEGRATION_CHECKOUT_TOKEN` is configured;
    see §5 below for the caveat this creates)
  - `Helm lint, render, schema-validate, policy-check` (backend
    `k8s-validate.yml`)
  - Frontend and OCR repos' own equivalent CI job names (their `ci.yml`
    jobs — not enumerated here since this proposal only has direct
    visibility into the backend repo's workflow files this phase; the
    same principle applies: pin to the exact job `name:` fields those
    repos' workflows already use).
- Do **not** require `release.yml`/`deploy-staging.yml`/
  `rollback-staging.yml` as a `main`-merge gate — those are
  `workflow_dispatch`-only, deliberately not triggered by every PR, and
  requiring them would make every PR un-mergeable by design.
- Block force-push and branch deletion.
- Restrict who can dismiss reviews (repo admins only).

## 4. Proposed rule set — `develop`

Same as `main` above, minus the "require branches up to date" rule
loosened to a recommendation rather than a hard block — `develop`
is a faster-moving integration branch and a hard up-to-date requirement
there tends to create rebase churn disproportionate to its benefit.
Everything else (required reviews, required status checks, no
force-push, no deletion) applies identically.

## 5. Known caveat this proposal deliberately surfaces

`integration.yml`'s `Compose build, up, smoke tests` job only runs when
`INTEGRATION_CHECKOUT_TOKEN` is configured as a repo secret (it checks
out the frontend/OCR repos as siblings, which needs cross-repo access) —
otherwise `Integration tests skipped (no INTEGRATION_CHECKOUT_TOKEN)`
runs instead. GitHub's required-status-checks feature matches by job
name, not by "one of these two mutually exclusive jobs" — so requiring
`Compose build, up, smoke tests` specifically would **permanently block
merging** in any fork or clone where that secret isn't set (its check
would simply never appear, and GitHub treats a required check that never
runs as permanently pending, not as satisfied). Recommendation: only add
this specific check as required once `INTEGRATION_CHECKOUT_TOKEN` is
confirmed permanently configured for this exact repository — do not
add it blindly from this proposal.

## 6. What this proposal does not cover

- No change to who has push/admin access to either branch — that's an
  org-level decision, not a workflow-file concern.
- No production-environment approval gating rule — see `RELEASE.md`
  §"GitHub Environments" for the prepared-but-inactive `production`
  environment, which is a separate mechanism (environment protection
  rules) from branch protection.
