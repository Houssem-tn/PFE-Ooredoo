#!/usr/bin/env python3
"""Validate a release manifest against deploy/releases/schema/release.schema.json.

Usage: python validate-release-manifest.py <manifest.json> [<manifest.json> ...]

Exits 1 and prints every violation if any manifest fails to validate, or if
a manifest contains anything that looks like a real secret (a heuristic
belt-and-braces check on top of the schema, since the schema alone can't
express "no field's value looks like a credential").
"""
import json
import re
import sys
from pathlib import Path

try:
    import jsonschema
except ImportError:
    print("ERROR: the 'jsonschema' package is required (pip install jsonschema)", file=sys.stderr)
    sys.exit(1)

SCHEMA_PATH = Path(__file__).resolve().parent.parent / "releases" / "schema" / "release.schema.json"

# Heuristic guard: a release manifest must never contain anything that looks
# like a real secret, token or private key, even though the schema's
# additionalProperties:false already blocks unexpected fields from holding
# one. This exists in case a future field is added carelessly.
SUSPICIOUS_KEY_PATTERN = re.compile(r"(password|secret|token|private[_-]?key|kubeconfig)", re.IGNORECASE)


def check_no_secrets(obj, path="$"):
    findings = []
    if isinstance(obj, dict):
        for key, value in obj.items():
            key_path = f"{path}.{key}"
            if SUSPICIOUS_KEY_PATTERN.search(key):
                findings.append(f"{key_path}: field name looks credential-shaped")
            findings.extend(check_no_secrets(value, key_path))
    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            findings.extend(check_no_secrets(item, f"{path}[{i}]"))
    elif isinstance(obj, str):
        if "BEGIN " in obj and "PRIVATE KEY" in obj:
            findings.append(f"{path}: value looks like a PEM private key")
        if re.match(r"^ghp_[A-Za-z0-9]{36}$", obj) or re.match(r"^gho_[A-Za-z0-9]{36}$", obj):
            findings.append(f"{path}: value looks like a GitHub token")
    return findings


def main(argv):
    if not argv:
        print("Usage: validate-release-manifest.py <manifest.json> [...]", file=sys.stderr)
        return 1

    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator_cls = jsonschema.validators.validator_for(schema)
    validator_cls.check_schema(schema)
    validator = validator_cls(schema)

    any_bad = False
    for manifest_path in argv:
        data = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
        errors = sorted(validator.iter_errors(data), key=lambda e: list(e.path))
        secret_findings = check_no_secrets(data)

        if errors or secret_findings:
            any_bad = True
            print(f"FAIL: {manifest_path}")
            for e in errors:
                loc = "$" + "".join(f"[{p!r}]" if isinstance(p, str) else f"[{p}]" for p in e.path)
                print(f"  schema violation at {loc}: {e.message}")
            for finding in secret_findings:
                print(f"  secret-shaped content: {finding}")
        else:
            print(f"OK: {manifest_path}")

    return 1 if any_bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
