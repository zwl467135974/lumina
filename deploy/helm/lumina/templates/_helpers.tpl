{{/*
Lumina 辅助模板
*/}}

{{- define "lumina.fullname" -}}
{{- .Release.Name -}}
{{- end -}}

{{- define "lumina.labels" -}}
app.kubernetes.io/name: lumina
app.kubernetes.io/instance: {{ .Release.Name }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "lumina.image" -}}
{{- .registry }}/{{ .namespace }}/{{ .name }}:{{ .tag | default .globalTag -}}
{{- end -}}
