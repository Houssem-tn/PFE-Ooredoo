{{/*
Chart name/version label value (Helm's own convention).
*/}}
{{- define "ooredoo.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Standard labels, applied to every resource. Component-level Services are
deliberately named with fixed short names (backend/frontend/ocr/mongodb/
minio — see services.yaml) rather than release-prefixed names, so the
frontend's nginx.conf and the backend's env-var-driven service URLs (baked
against Docker Compose's own service-name DNS — see values.yaml's
mongodb/minio/ocr URL comments) resolve unchanged in Kubernetes without
rebuilding any image. One consequence, by design: only one release of
this chart per namespace (which is already the model — one release per
environment namespace: ooredoo-staging, ooredoo-prod, ...).
*/}}
{{- define "ooredoo.labels" -}}
helm.sh/chart: {{ include "ooredoo.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: ooredoo
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{/*
Per-component selector labels. Call as:
  {{ include "ooredoo.selectorLabels" (dict "name" "backend" "context" $) }}
Kept separate from ooredoo.labels because selector labels must never
change on an existing Deployment/StatefulSet across upgrades — everything
else in ooredoo.labels is safe to add to over time, this isn't.
*/}}
{{- define "ooredoo.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
{{- end -}}

{{/*
Namespace every resource is rendered into.
*/}}
{{- define "ooredoo.namespace" -}}
{{- .Values.namespace.name -}}
{{- end -}}
