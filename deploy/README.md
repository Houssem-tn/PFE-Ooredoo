# CI-consumed mirror of the Compose files

The canonical, human-facing copies of these files live at the `Pfe/` repo
root (sibling to `APPSTOCK/`, `Pfe-Oreedoo-Front/`, `EacyOCR/`) — see
`DOCKER.md` there for full usage. **Edit them there.**

The files in this directory (`docker-compose.yml`, `docker-compose.debug.yml`,
`docker-compose.prod-check.yml`, `.env.docker.example`) are byte-identical
copies, kept here **only** because `.github/workflows/integration.yml` runs
in GitHub Actions, which can only check out files that live inside a git
repository — and the canonical copies at `Pfe/` don't live inside any of
the three project repos (that directory itself has no `.git`).

**After editing the canonical files at `Pfe/`, re-sync this copy:**

```bash
# from the Pfe/ repo root
cp docker-compose.yml docker-compose.debug.yml docker-compose.prod-check.yml \
   .env.docker.example \
   APPSTOCK/PFE-Ooredoo/deploy/
```

`integration.yml` does not verify the two copies match — a stale copy here
will silently test outdated Compose config. If this drifts often enough to
be a real problem, replace this manual copy step with a small script or a
pre-commit hook; it wasn't worth the added complexity for a file that
changes rarely.

`ci-scripts/` is not a mirror of anything — it's backend-only tooling used
directly by `integration.yml` (currently just `ws_stomp_probe.py`, a STOMP
CONNECT probe for the WebSocket auth smoke tests; see its own docstring).
