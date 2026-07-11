# Reproduces .github/workflows/ci.yml's `validate` + `dependency-check` jobs
# locally. Thin wrapper around the same mvnw commands CI runs — no separate
# logic to keep in sync. Requires a local MongoDB reachable at $env:MONGODB_URI
# (defaults to a disposable localhost instance — never point this at a real
# dev/prod database).
#
# Usage: .\scripts\ci-local.ps1 [-SkipDependencyCheck]
param(
    [switch]$SkipDependencyCheck
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$env:SPRING_PROFILES_ACTIVE = "test"
if (-not $env:MONGODB_URI) { $env:MONGODB_URI = "mongodb://localhost:27017/pfe_ci_local" }
if (-not $env:APP_JWT_SECRET) { $env:APP_JWT_SECRET = "ci-local-test-only-jwt-secret-0000000000" }

Write-Host "==> clean verify (Enforcer + compile + tests + JaCoCo + Checkstyle/SpotBugs reports)"
& .\mvnw.cmd --batch-mode clean verify
if ($LASTEXITCODE -ne 0) { throw "clean verify failed" }

Write-Host "==> Checkstyle blocking gate"
& .\mvnw.cmd --batch-mode checkstyle:check
if ($LASTEXITCODE -ne 0) { throw "Checkstyle failed" }

Write-Host "==> SpotBugs blocking gate"
& .\mvnw.cmd --batch-mode spotbugs:check
if ($LASTEXITCODE -ne 0) { throw "SpotBugs failed" }

if (-not $SkipDependencyCheck) {
    Write-Host "==> OWASP Dependency-Check (slow on first run; set NVD_API_KEY for a reliable sync)"
    & .\mvnw.cmd --batch-mode dependency-check:check
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Dependency-Check did not pass cleanly — see target/dependency-check-report.html (non-blocking locally without NVD_API_KEY, same as CI)."
    }
}

if (Get-Command gitleaks -ErrorAction SilentlyContinue) {
    Write-Host "==> Gitleaks (full history)"
    gitleaks detect --config .gitleaks.toml --no-banner
} else {
    Write-Host "==> Gitleaks not installed locally — skipping (CI still runs it)"
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "==> Docker build (validation only, no push)"
    docker build -t ooredoo-backend:ci-local .
} else {
    Write-Host "==> Docker not installed locally — skipping image build (CI still runs it)"
}

Write-Host "==> Done. Reports: target/site/jacoco/, target/checkstyle-result.xml, target/spotbugsXml.xml, target/dependency-check-report.html"
