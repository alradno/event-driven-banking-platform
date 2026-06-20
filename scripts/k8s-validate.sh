#!/usr/bin/env sh
set -eu

CHART_PATH="${CHART_PATH:-deploy/helm/banking-platform}"
K8S_RELEASE="${K8S_RELEASE:-banking}"
K8S_NAMESPACE="${K8S_NAMESPACE:-banking}"
HELM="${HELM:-helm}"
HELM_EXTRA_ARGS="${HELM_EXTRA_ARGS:-}"

helm_cmd() {
  # HELM may intentionally contain a multi-word containerized runner command.
  # shellcheck disable=SC2086
  $HELM "$@"
}

fail() {
  echo "Kubernetes validation failed: $*" >&2
  exit 2
}

count_literal() {
  pattern="$1"
  grep -F "$pattern" "$rendered" | wc -l | tr -d ' '
}

count_exact() {
  pattern="$1"
  grep -Fx "$pattern" "$rendered" | wc -l | tr -d ' '
}

require_count() {
  pattern="$1"
  expected="$2"
  actual="$(count_literal "$pattern")"
  [ "$actual" = "$expected" ] || fail "expected $expected occurrences of '$pattern', found $actual"
}

require_exact_count() {
  pattern="$1"
  expected="$2"
  actual="$(count_exact "$pattern")"
  [ "$actual" = "$expected" ] || fail "expected $expected exact lines of '$pattern', found $actual"
}

require_contains() {
  pattern="$1"
  grep -Fq "$pattern" "$rendered" || fail "missing rendered manifest contract: $pattern"
}

case "$HELM" in
  *" "*) ;;
  *) command -v "$HELM" >/dev/null 2>&1 || fail "helm is not installed; set HELM to a containerized runner command" ;;
esac

rendered="$(mktemp)"
cleanup() {
  rm -f "$rendered"
}
trap cleanup EXIT

# shellcheck disable=SC2086
helm_cmd lint --strict "$CHART_PATH" $HELM_EXTRA_ARGS
# shellcheck disable=SC2086
helm_cmd template "$K8S_RELEASE" "$CHART_PATH" --namespace "$K8S_NAMESPACE" $HELM_EXTRA_ARGS > "$rendered"

require_exact_count "kind: Deployment" 7
require_exact_count "kind: Service" 7
require_exact_count "kind: ServiceAccount" 1
require_exact_count "kind: ConfigMap" 1
require_exact_count "kind: Secret" 1
require_count "readinessProbe:" 7
require_count "livenessProbe:" 7
require_count "prometheus.io/scrape: \"true\"" 6

for service in \
  api-gateway \
  customer-service \
  account-service \
  payment-service \
  audit-service \
  notification-service \
  ai-incident-assistant
do
  occurrences="$(count_literal "name: banking-platform-$service")"
  [ "$occurrences" -ge 2 ] || fail "expected Deployment and Service names for banking-platform-$service"
done

require_contains "name: banking-platform-postgres"
require_contains "SPRING_DATASOURCE_USERNAME:"
require_contains "SPRING_DATASOURCE_PASSWORD:"
require_contains "KAFKA_BOOTSTRAP_SERVERS:"
require_contains "REDIS_HOST:"
require_contains "REDIS_PORT:"
require_contains "KEYCLOAK_ISSUER_URI:"
require_contains "KEYCLOAK_JWK_SET_URI:"
require_contains "MANAGEMENT_OTLP_TRACING_ENDPOINT:"
require_contains "MANAGEMENT_TRACING_SAMPLING_PROBABILITY:"
require_contains "OTEL_EXPORTER_OTLP_ENDPOINT:"
require_contains "JAVA_TOOL_OPTIONS:"

require_contains "value: \"http://banking-platform-customer-service:8080\""
require_contains "value: \"http://banking-platform-account-service:8080\""
require_contains "value: \"http://banking-platform-payment-service:8080\""
require_contains "value: \"http://banking-platform-audit-service:8080\""
require_contains "value: \"http://banking-platform-notification-service:8080\""
require_contains "value: \"http://banking-platform-ai-incident-assistant:8090\""
require_contains "value: \"jdbc:postgresql://postgres:5432/customer_db\""
require_contains "value: \"jdbc:postgresql://postgres:5432/account_db\""
require_contains "value: \"jdbc:postgresql://postgres:5432/payment_db\""
require_contains "value: \"jdbc:postgresql://postgres:5432/audit_db\""

echo "Kubernetes manifest validation passed: 7 Deployments, 7 Services, probes, scrape annotations, config, and default Postgres Secret are consistent."
