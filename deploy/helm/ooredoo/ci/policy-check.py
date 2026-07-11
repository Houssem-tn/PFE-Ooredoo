#!/usr/bin/env python3
"""Minimal policy checks for rendered Kubernetes manifests.

Not a replacement for Checkov/Trivy config scanning (neither was
available in the environment this was first authored in — see
Pfe/KUBERNETES.md "Validation" for how to add them for real in CI) — this
covers exactly the specific checks the Kubernetes-prep task asked for and
nothing else: forbidden `:latest` tags, missing resource requests/limits,
missing probes, privileged containers, missing pod/container security
contexts. Reads one or more rendered manifest files (i.e. the output of
`helm template`), not chart templates directly.

Usage:
    helm template ooredoo . -f values-staging.yaml | python3 ci/policy-check.py -
    python3 ci/policy-check.py rendered.yaml [rendered2.yaml ...]

Exit code 0 = no findings, 1 = at least one finding.
"""
import sys

import yaml

WORKLOAD_KINDS = {"Deployment", "StatefulSet", "DaemonSet", "Job", "CronJob"}


def pod_spec_of(doc):
    kind = doc.get("kind")
    if kind == "CronJob":
        return doc["spec"]["jobTemplate"]["spec"]["template"]["spec"]
    if kind in WORKLOAD_KINDS:
        return doc["spec"]["template"]["spec"]
    return None


def check_doc(doc, findings):
    kind = doc.get("kind")
    name = doc.get("metadata", {}).get("name", "<unnamed>")
    ns = doc.get("metadata", {}).get("namespace", "<no-namespace>")
    ref = f"{kind}/{name} (ns={ns})"

    pod_spec = pod_spec_of(doc)
    if pod_spec is None:
        return

    if "securityContext" not in pod_spec or not pod_spec["securityContext"]:
        findings.append(f"{ref}: pod-level securityContext is missing")

    containers = pod_spec.get("containers", []) + pod_spec.get("initContainers", [])
    for c in containers:
        cref = f"{ref} container={c.get('name', '<unnamed>')}"

        image = c.get("image", "")
        tag = image.rsplit(":", 1)[-1] if ":" in image.rsplit("/", 1)[-1] else None
        if tag is None or tag == "latest":
            findings.append(f"{cref}: image '{image}' has no pinned tag, or uses ':latest'")

        resources = c.get("resources") or {}
        if not resources.get("requests"):
            findings.append(f"{cref}: missing resources.requests")
        if not resources.get("limits"):
            findings.append(f"{cref}: missing resources.limits")

        # Jobs and CronJobs (e.g. minio-init, mongodb-backup) are
        # short-lived and exit on their own — probes don't apply to them
        # the way they do to long-running workloads.
        if kind not in ("Job", "CronJob"):
            if "readinessProbe" not in c:
                findings.append(f"{cref}: missing readinessProbe")
            if "livenessProbe" not in c:
                findings.append(f"{cref}: missing livenessProbe")

        sc = c.get("securityContext")
        if not sc:
            findings.append(f"{cref}: container-level securityContext is missing")
        else:
            if sc.get("privileged") is True:
                findings.append(f"{cref}: privileged: true is forbidden")
            if sc.get("allowPrivilegeEscalation") is not False:
                findings.append(f"{cref}: allowPrivilegeEscalation must be explicitly false")
            caps = (sc.get("capabilities") or {}).get("drop") or []
            if "ALL" not in caps:
                findings.append(f"{cref}: capabilities.drop must include ALL")

    if pod_spec.get("hostNetwork") is True:
        findings.append(f"{ref}: hostNetwork: true is forbidden")
    for v in pod_spec.get("volumes", []):
        if "hostPath" in v:
            findings.append(f"{ref}: volume '{v.get('name')}' uses hostPath (needs explicit justification)")


def main():
    args = sys.argv[1:] or ["-"]
    findings = []
    for path in args:
        text = sys.stdin.read() if path == "-" else open(path, encoding="utf-8").read()
        for doc in yaml.safe_load_all(text):
            if doc:
                check_doc(doc, findings)

    if findings:
        print(f"Policy check: {len(findings)} finding(s)\n")
        for f in findings:
            print(f"  - {f}")
        sys.exit(1)

    print("Policy check: no findings.")
    sys.exit(0)


if __name__ == "__main__":
    main()
