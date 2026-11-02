{{/*
Chart name, overridable.
*/}}
{{- define "menura.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name. If the release name already contains the chart name,
the release name is used on its own.
*/}}
{{- define "menura.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "menura.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "menura.selectorLabels" -}}
app.kubernetes.io/name: {{ include "menura.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Labels of one component. Call with a dict: (dict "root" $ "component" "server")
*/}}
{{- define "menura.componentLabels" -}}
helm.sh/chart: {{ include "menura.chart" .root }}
{{ include "menura.selectorLabels" .root }}
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: menura
{{- end }}

{{/*
Selector labels of one component. Call with a dict: (dict "root" $ "component" "server")
*/}}
{{- define "menura.componentSelectorLabels" -}}
{{ include "menura.selectorLabels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Image of one component: its repository from values, the tag from values or,
when empty, the chart's appVersion. Call with a dict: (dict "root" $ "component" "server")
*/}}
{{- define "menura.image" -}}
{{- $tag := default .root.Chart.AppVersion .root.Values.image.tag -}}
{{- printf "%s:%s" (index .root.Values .component "image" "repository") $tag -}}
{{- end }}

{{/*
Database environment every server, job and migrate container gets. The URL is
plain configuration; the credentials come from the existing Secret.
*/}}
{{- define "menura.databaseEnv" -}}
- name: DATASOURCES_DEFAULT_URL
  value: {{ .Values.database.url | quote }}
{{- end }}

{{- define "menura.databaseEnvFrom" -}}
{{- with .Values.database.existingSecret }}
envFrom:
  - secretRef:
      name: {{ . }}
{{- end }}
{{- end }}

{{/*
Pod-level hardening shared by every pod: no Kubernetes API token and no root.
*/}}
{{- define "menura.podSecurity" -}}
automountServiceAccountToken: false
securityContext:
  runAsNonRoot: true
  seccompProfile:
    type: RuntimeDefault
{{- end }}

{{- define "menura.containerSecurity" -}}
securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
{{- end }}
