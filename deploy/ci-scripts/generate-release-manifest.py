#!/usr/bin/env python3
"""Generate a release manifest JSON file (deploy/releases/schema/release.schema.json
shape) from CLI arguments. Used by .github/workflows/release.yml - never
hand-edited output. Never accepts or emits secret values; see
validate-release-manifest.py for the schema + heuristic secret check this
output must pass.
"""
import argparse
import json
import sys
from datetime import datetime, timezone


def image_ref(repo, tag, digest):
    return {"repository": repo, "tag": tag, "digest": digest}


def source_ref(repo, sha):
    return {"repository": repo, "commitSha": sha}


def scan_status(critical, high, gitleaks):
    return {"trivyCritical": critical, "trivyHigh": high, "gitleaks": gitleaks}


def sbom_ref(artifact_name):
    return {"artifactName": artifact_name, "format": "cyclonedx-json"}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--release-id", required=True)
    p.add_argument("--target-environment", required=True, choices=["staging", "production"])
    p.add_argument("--previous-release-id", default=None)

    for component in ("backend", "frontend", "ocr"):
        p.add_argument(f"--{component}-repo", required=True)
        p.add_argument(f"--{component}-sha", required=True)
        p.add_argument(f"--{component}-image-repo", required=True)
        p.add_argument(f"--{component}-image-tag", required=True)
        p.add_argument(f"--{component}-image-digest", required=True)
        p.add_argument(f"--{component}-ci-run-id", default=None)
        p.add_argument(f"--{component}-trivy-critical", default="pass", choices=["pass", "fail", "not_run"])
        p.add_argument(f"--{component}-trivy-high", default="pass", choices=["pass", "advisory_findings", "not_run"])
        p.add_argument(f"--{component}-gitleaks", default="pass", choices=["pass", "fail", "not_run"])
        p.add_argument(f"--{component}-sbom-artifact", required=True)

    p.add_argument("--chart-name", default="ooredoo")
    p.add_argument("--chart-version", required=True)
    p.add_argument("--helm-release-revision", type=int, default=None)
    p.add_argument("--release-run-id", default=None)
    p.add_argument("--deploy-staging-run-id", default=None)
    p.add_argument("--output", required=True)

    args = p.parse_args()

    # GitHub Actions passes blank optional inputs as empty strings, not
    # absent - normalize those back to None so the manifest matches the
    # schema's intent ("null when not known/applicable"), not a stray "".
    optional_fields = [
        "previous_release_id",
        "backend_ci_run_id", "frontend_ci_run_id", "ocr_ci_run_id",
        "helm_release_revision", "release_run_id", "deploy_staging_run_id",
    ]
    for field in optional_fields:
        if getattr(args, field) == "":
            setattr(args, field, None)

    manifest = {
        "releaseId": args.release_id,
        "releaseTimestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sources": {
            "backend": source_ref(args.backend_repo, args.backend_sha),
            "frontend": source_ref(args.frontend_repo, args.frontend_sha),
            "ocr": source_ref(args.ocr_repo, args.ocr_sha),
        },
        "images": {
            "backend": image_ref(args.backend_image_repo, args.backend_image_tag, args.backend_image_digest),
            "frontend": image_ref(args.frontend_image_repo, args.frontend_image_tag, args.frontend_image_digest),
            "ocr": image_ref(args.ocr_image_repo, args.ocr_image_tag, args.ocr_image_digest),
        },
        "helm": {
            "chartName": args.chart_name,
            "chartVersion": args.chart_version,
            "releaseRevision": args.helm_release_revision,
        },
        "workflowRuns": {
            "backendCi": args.backend_ci_run_id,
            "frontendCi": args.frontend_ci_run_id,
            "ocrCi": args.ocr_ci_run_id,
            "release": args.release_run_id,
            "deployStaging": args.deploy_staging_run_id,
        },
        "securityScans": {
            "backend": scan_status(args.backend_trivy_critical, args.backend_trivy_high, args.backend_gitleaks),
            "frontend": scan_status(args.frontend_trivy_critical, args.frontend_trivy_high, args.frontend_gitleaks),
            "ocr": scan_status(args.ocr_trivy_critical, args.ocr_trivy_high, args.ocr_gitleaks),
        },
        "sbom": {
            "backend": sbom_ref(args.backend_sbom_artifact),
            "frontend": sbom_ref(args.frontend_sbom_artifact),
            "ocr": sbom_ref(args.ocr_sbom_artifact),
        },
        "targetEnvironment": args.target_environment,
        "previousReleaseId": args.previous_release_id,
    }

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, sort_keys=True)
        f.write("\n")

    print(f"Wrote {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
