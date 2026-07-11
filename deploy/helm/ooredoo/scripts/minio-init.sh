#!/bin/sh
# Idempotent bucket-creation Job — see templates/job-minio-init.yaml, a
# Kubernetes port of deploy/docker-compose.yml's own minio-init service.
# Waits for MinIO to accept authenticated requests (a Job has no
# depends_on: condition: service_healthy equivalent), then ensures the
# bucket exists. Safe to re-run on every Helm upgrade.
set -eu

echo "Waiting for MinIO to accept authenticated requests..."
i=0
until mc alias set local "http://minio:${MINIO_PORT}" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" && mc admin info local >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 30 ]; then
    echo "MinIO did not become ready in time" >&2
    exit 1
  fi
  sleep 5
done

echo "MinIO is ready — ensuring bucket '${MINIO_BUCKET}' exists (idempotent)."
mc mb --ignore-existing "local/${MINIO_BUCKET}"
echo "Done."
