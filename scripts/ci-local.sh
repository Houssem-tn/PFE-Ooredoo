#!/usr/bin/env bash
# Reproduces .github/workflows/ci.yml's `validate` + `dependency-check` jobs
# locally. Thin wrapper around the same mvnw commands CI runs — no separate
# logic to keep in sync. Requires a local MongoDB reachable at $MONGODB_URI
# (defaults to a disposable localhost instance — never point this at a real
# dev/prod database).
#
# Usage: ./scripts/ci-local.sh [--skip-dependency-check]
set -euo pipefail
cd "$(dirname "$0")/.."

export SPRING_PROFILES_ACTIVE=test
export MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27017/pfe_ci_local}"
export APP_JWT_SECRET="${APP_JWT_SECRET:-ci-local-test-only-jwt-secret-0000000000}"

echo "==> clean verify (Enforcer + compile + tests + JaCoCo + Checkstyle/SpotBugs reports)"
./mvnw --batch-mode clean verify

echo "==> Checkstyle blocking gate"
./mvnw --batch-mode checkstyle:check

echo "==> SpotBugs blocking gate"
./mvnw --batch-mode spotbugs:check

if [[ "${1:-}" != "--skip-dependency-check" ]]; then
  echo "==> OWASP Dependency-Check (slow on first run; set NVD_API_KEY for a reliable sync)"
  ./mvnw --batch-mode dependency-check:check || echo "Dependency-Check did not pass cleanly — see target/dependency-check-report.html (non-blocking locally without NVD_API_KEY, same as CI)."
fi

if command -v gitleaks >/dev/null 2>&1; then
  echo "==> Gitleaks (full history)"
  gitleaks detect --config .gitleaks.toml --no-banner
else
  echo "==> Gitleaks not installed locally — skipping (CI still runs it)"
fi

if command -v docker >/dev/null 2>&1; then
  echo "==> Docker build (validation only, no push)"
  docker build -t ooredoo-backend:ci-local .
else
  echo "==> Docker not installed locally — skipping image build (CI still runs it)"
fi

echo "==> Done. Reports: target/site/jacoco/, target/checkstyle-result.xml, target/spotbugsXml.xml, target/dependency-check-report.html"
