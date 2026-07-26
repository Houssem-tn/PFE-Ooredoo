# Production readiness audit (Phase 5)

Scope: audit and document what's needed before this chart is ever
installed against a real production cluster. **Nothing in this document
was applied to any cluster.** `values-production.yaml` remains an
unused overlay with placeholder domain/TLS values, exactly as it was
before this phase — see its own header comment.

## 1. What's already in place (verified by reading the chart, not assumed)

| Control | Status | Where |
|---|---|---|
| Non-root, no-privilege-escalation, dropped-capabilities, `seccompProfile: RuntimeDefault` on every workload | Done | `values.yaml` `*.securityContext` blocks for backend/frontend/ocr/mongodb/minio + backup CronJobs + minio-init Job |
| `readOnlyRootFilesystem` | Per-workload, justified exceptions documented inline (nginx/mongod/minio/ocr need writable paths; backend's JVM does not and is `true`) | same file, each block's own comment |
| PodDisruptionBudgets | Implemented, off by default | `templates/pdb.yaml`, enabled in `values-production.yaml` (`minAvailable: 1` for backend/frontend) |
| HorizontalPodAutoscalers | Implemented (CPU-utilization based), off by default — requires metrics-server | `templates/hpa.yaml` |
| NetworkPolicies | 6 policies implemented, off by default, enabled in `values-production.yaml` | `templates/networkpolicies.yaml` |
| Ingress + TLS (cert-manager) | Implemented, placeholder domain in the production overlay | `templates/ingress.yaml`, `values-production.yaml` |
| Resource requests/limits per workload | Set in the production overlay for every component | `values-production.yaml` |
| Backup CronJobs (Mongo + MinIO) | Implemented, verified working this phase (§ see `BACKUP_RESTORE.md`) | `templates/cronjob-*.yaml` |
| Monitoring (ServiceMonitors, PrometheusRules, Grafana dashboards) | Implemented, proven working in Phase 4 | `templates/servicemonitor.yaml`, `templates/prometheusrule.yaml`, `templates/grafana-dashboards.yaml` |
| Image digest-pinning support | Added this phase | `templates/_helpers.tpl`'s `ooredoo.image` |
| `existingSecret` escape hatch for a real secret manager | Already present pre-Phase 5 | `values.yaml`, see `SECRETS.md` §3 |

## 2. Real gaps — not yet addressed, called out rather than glossed over

1. **No dedicated ServiceAccount/RBAC per workload.** Every pod runs
   under the namespace's `default` ServiceAccount with no explicit
   `Role`/`RoleBinding`. None of these workloads currently need
   Kubernetes API access, so the practical exposure is low, but
   production hardening should still create per-workload
   ServiceAccounts with `automountServiceAccountToken: false` (the
   default token mount is otherwise pure attack surface with no
   corresponding need). Not implemented this phase.
2. **PVC reclaim policy is whatever the cluster's default StorageClass
   uses**, never set explicitly. On k3d's `local-path` that's typically
   `Delete` — acceptable for a disposable staging cluster, **not**
   acceptable for production, where a StatefulSet PVC's backing volume
   being deleted on PVC deletion turns an accidental `kubectl delete pvc`
   into unrecoverable data loss with no extra confirmation step. Before
   production: use (or create) a StorageClass with
   `reclaimPolicy: Retain` for `mongodb-data`/`minio-data`/`backup-store`.
3. **No formal log retention/shipping.** Prometheus/Grafana retention
   defaults (whatever `kube-prometheus-stack` ships with, currently
   unmodified) are the only "retention" configured; there's no log
   aggregation (Loki, ELK, cloud logging) at all. Pod logs are only as
   durable as `kubectl logs`/the container runtime's own log rotation.
4. **No alertmanager routing configured** beyond what
   `kube-prometheus-stack`'s own defaults provide — `PrometheusRule`
   objects fire alerts, but nothing routes them to email/Slack/PagerDuty.
   Needs a real `AlertmanagerConfig`/`alertmanager.yaml` once there's an
   actual on-call destination.
5. **DNS and firewall requirements are not yet defined** because the
   real production domain/network boundary aren't known yet — this
   phase's task explicitly says not to configure the final public domain
   or a production TLS certificate. What's known structurally: the
   ingress controller needs a public IP/LB with 443 (and optionally 80
   for ACME HTTP-01, if that challenge type is used) reachable from the
   internet or from wherever cert-manager's ACME solver runs from; the
   Kubernetes API server itself must **never** be exposed publicly (see
   §4 for the k3d-specific version of this rule, which generalizes
   directly).
6. **Backup destination is still PVC-mode, not S3**, in the production
   overlay (`backup.enabled: false` there, pending real external storage
   — see `values-production.yaml`'s own comment on this).
7. **No production `GitHub Environment`** with manual-approval gating
   has been activated — see `RELEASE.md` §"GitHub Environments" for the
   prepared-but-inactive state.

## 3. Sizing — Phase 4 observed usage as a MINIMUM reference only

Real `kubectl top pod` measurements from the live k3d staging cluster
(Phase 4), reproduced here because this phase's own task explicitly
requires using them only as a floor, not as final production sizing:

| Component | Observed (staging, light load) |
|---|---|
| backend | 442 MiB |
| ocr | 876 MiB |
| mongodb | 97 MiB |
| minio | 250 MiB |
| prometheus | 377 MiB |
| grafana | 370 MiB |
| **node total** | **~5.1 GiB** |

These numbers reflect a single-replica, no-real-traffic k3d environment.
**Do not** copy them directly into a production sizing decision — real
production sizing needs: expected concurrent user count, real OCR job
throughput (OCR is by far the heaviest single component observed, and
scales per-job, not per-user), MongoDB working-set size once real
production data volume exists, and whatever HPA/replica-count strategy
is chosen once metrics-server is available. Until an actual target
enterprise machine's specs are known, no final CPU/memory/replica
numbers should be committed to `values-production.yaml` beyond the
current placeholder resource requests/limits already there.

## 4. Registry access

Production should use the same GHCR-digest-pinned pattern proven this
phase for staging (`SECRETS.md` §1's `ghcr-secret`), with its own
independently-scoped `Packages: Read-only` credential — never reuse the
staging pull secret's underlying token for production, even though both
would point at the same public/private package.

## 5. Cluster sizing / topology

Not a k3d single-node topology in production — at minimum: a real
multi-node cluster (or managed Kubernetes service) with the API server
never exposed to the public internet (bastion/VPN/private-endpoint
access only), a proper LoadBalancer or Ingress-fronting solution instead
of k3d's built-in `serverlb`, and a StorageClass backed by real
replicated storage (not `local-path`, which ties a PVC to a single
node's local disk — acceptable for a disposable dev cluster, a single
point of failure in production).
