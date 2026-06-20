{{- define "banking-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "banking-platform.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "banking-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "banking-platform.labels" -}}
helm.sh/chart: {{ include "banking-platform.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: banking-platform
{{- end -}}

{{- define "banking-platform.selectorLabels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: banking-platform
{{- end -}}

{{- define "banking-platform.serviceName" -}}
{{- printf "%s-%s" (include "banking-platform.fullname" .root) .service.name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "banking-platform.serviceLabels" -}}
{{ include "banking-platform.labels" .root }}
app.kubernetes.io/name: {{ .service.name }}
app.kubernetes.io/component: {{ .service.name }}
{{- end -}}

{{- define "banking-platform.serviceSelectorLabels" -}}
{{ include "banking-platform.selectorLabels" .root }}
app.kubernetes.io/name: {{ .service.name }}
app.kubernetes.io/component: {{ .service.name }}
{{- end -}}

{{- define "banking-platform.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "banking-platform.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "banking-platform.postgresSecretName" -}}
{{- if .Values.postgres.existingSecret -}}
{{- .Values.postgres.existingSecret -}}
{{- else -}}
{{- printf "%s-postgres" (include "banking-platform.fullname" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
