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
plain configuration (MENURA_DB_URL, read by core MenuraSettings and the server's
application.yml); the user and password come from the existing Secret.
*/}}
{{- define "menura.databaseEnv" -}}
- name: MENURA_DB_URL
  value: {{ .Values.database.url | quote }}
{{- end }}

{{/*
Library environment of the server only: the object storage endpoint, bucket and region, the part size and the
limits and lifetimes of uploads and signed links. The credentials are not here; they come from storage.existingSecret.
*/}}
{{- define "menura.libraryEnv" -}}
- name: MENURA_S3_ENDPOINT
  value: {{ .Values.storage.endpoint | quote }}
- name: MENURA_S3_REGION
  value: {{ .Values.storage.region | quote }}
- name: MENURA_S3_BUCKET
  value: {{ .Values.storage.bucket | quote }}
# Byte counts are rendered as int64: a YAML number is a float, which quote would print in exponent form.
- name: MENURA_LIBRARY_PART_SIZE_BYTES
  value: {{ int64 .Values.library.partSizeBytes | quote }}
- name: MENURA_LIBRARY_RAW_MATERIAL_MAX_BYTES
  value: {{ int64 .Values.library.rawMaterialMaxBytes | quote }}
- name: MENURA_LIBRARY_UPLOAD_URL_LIFETIME
  value: {{ .Values.library.uploadUrlLifetime | quote }}
- name: MENURA_LIBRARY_LINK_LIFETIME
  value: {{ .Values.library.linkLifetime | quote }}
{{- end }}

{{- define "menura.databaseEnvFrom" -}}
{{- with .Values.database.existingSecret }}
envFrom:
  - secretRef:
      name: {{ . }}
{{- end }}
{{- end }}

{{/*
The env of the channel connections: the YouTube redirect URI and the refresh margin. The client id and secret and the
token keys are read from channels.existingSecret (menura.secretEnvFrom), never written here.
*/}}
{{- define "menura.channelEnv" -}}
- name: MENURA_YOUTUBE_REDIRECT_URI
  value: {{ .Values.channels.youtube.redirectUri | quote }}
- name: MENURA_CHANNEL_REFRESH_MARGIN
  value: {{ .Values.channels.refreshMargin | quote }}
{{- end }}

{{/*
Every Secret a server or jobs pod reads through envFrom: the database credentials and the channel secrets. The channel
Secret is listed only when it is set; without it the server does not start (the token key is required).
*/}}
{{- define "menura.secretEnvFrom" -}}
{{- $secrets := list -}}
{{- with .Values.database.existingSecret -}}{{- $secrets = append $secrets . -}}{{- end -}}
{{- with .Values.channels.existingSecret -}}{{- $secrets = append $secrets . -}}{{- end -}}
{{- include "menura.envFromSecrets" $secrets -}}
{{- end }}

{{/*
The server's envFrom: the shared Secrets plus, on the server only, the object storage Secret (S3 credentials).
*/}}
{{- define "menura.serverSecretEnvFrom" -}}
{{- $secrets := list -}}
{{- with .Values.database.existingSecret -}}{{- $secrets = append $secrets . -}}{{- end -}}
{{- with .Values.channels.existingSecret -}}{{- $secrets = append $secrets . -}}{{- end -}}
{{- with .Values.storage.existingSecret -}}{{- $secrets = append $secrets . -}}{{- end -}}
{{- include "menura.envFromSecrets" $secrets -}}
{{- end }}

{{/*
Renders envFrom for a list of Secret names; renders nothing for an empty list.
*/}}
{{- define "menura.envFromSecrets" -}}
{{- if . }}
envFrom:
  {{- range . }}
  - secretRef:
      name: {{ . }}
  {{- end }}
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
