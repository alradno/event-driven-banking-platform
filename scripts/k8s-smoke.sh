#!/usr/bin/env sh
set -eu

CHART_PATH="${CHART_PATH:-deploy/helm/banking-platform}"
K8S_RELEASE="${K8S_RELEASE:-banking}"
K8S_NAMESPACE="${K8S_NAMESPACE:-banking}"
K8S_SMOKE_APPLY="${K8S_SMOKE_APPLY:-false}"
K8S_SMOKE_TIMEOUT="${K8S_SMOKE_TIMEOUT:-5m}"
K8S_FULLNAME="${K8S_FULLNAME:-banking-platform}"
K8S_GATEWAY_LOCAL_PORT="${K8S_GATEWAY_LOCAL_PORT:-28080}"
K8S_AI_LOCAL_PORT="${K8S_AI_LOCAL_PORT:-28090}"
HELM="${HELM:-helm}"
HELM_EXTRA_ARGS="${HELM_EXTRA_ARGS:-}"
PF_PIDS=""

cleanup() {
  for pid in $PF_PIDS; do
    kill "$pid" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

fail() {
  echo "Kubernetes smoke blocked: $*" >&2
  exit 2
}

helm_cmd() {
  # HELM may intentionally contain a multi-word containerized runner command.
  # shellcheck disable=SC2086
  $HELM "$@"
}

wait_for_http() {
  label="$1"
  url="$2"
  attempts=60
  while [ "$attempts" -gt 0 ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    attempts=$((attempts - 1))
    sleep 2
  done
  fail "$label did not respond at $url"
}

port_forward() {
  service_name="$1"
  local_port="$2"
  remote_port="$3"
  kubectl -n "$K8S_NAMESPACE" port-forward "svc/$service_name" "$local_port:$remote_port" >/tmp/"$service_name".port-forward.log 2>&1 &
  PF_PIDS="$PF_PIDS $!"
}

command -v kubectl >/dev/null 2>&1 || fail "kubectl is not installed"
command -v curl >/dev/null 2>&1 || fail "curl is not installed"

if ! K8S_CONTEXT="$(kubectl config current-context 2>/dev/null)"; then
  fail "kubectl current-context is not set; create or select a cluster before running this smoke"
fi

[ -n "$K8S_CONTEXT" ] || fail "kubectl current-context is empty"

if ! kubectl cluster-info >/dev/null 2>&1; then
  fail "kubectl context '$K8S_CONTEXT' is not reachable"
fi

case "$HELM" in
  *" "*) ;;
  *) command -v "$HELM" >/dev/null 2>&1 || fail "helm is not installed; set HELM to a containerized runner command" ;;
esac

# shellcheck disable=SC2086
helm_cmd lint --strict "$CHART_PATH" $HELM_EXTRA_ARGS

if [ "$K8S_SMOKE_APPLY" = "true" ]; then
  # shellcheck disable=SC2086
  helm_cmd upgrade --install "$K8S_RELEASE" "$CHART_PATH" \
    --namespace "$K8S_NAMESPACE" \
    --create-namespace \
    --wait \
    --timeout "$K8S_SMOKE_TIMEOUT" \
    --atomic \
    $HELM_EXTRA_ARGS

  kubectl -n "$K8S_NAMESPACE" rollout status deployment \
    -l "app.kubernetes.io/instance=$K8S_RELEASE" \
    --timeout="$K8S_SMOKE_TIMEOUT"
  pod_report="$(kubectl -n "$K8S_NAMESPACE" get pods -l "app.kubernetes.io/instance=$K8S_RELEASE" \
    -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.phase}{" "}{range .status.containerStatuses[*]}{.restartCount}{" "}{.state.waiting.reason}{" "}{end}{"\n"}{end}')"
  if printf '%s\n' "$pod_report" | grep -Eq 'ImagePullBackOff|CrashLoopBackOff|ErrImagePull'; then
    printf '%s\n' "$pod_report" >&2
    fail "one or more pods are in image pull or crash loop state"
  fi

  kubectl -n "$K8S_NAMESPACE" get pods \
    -l "app.kubernetes.io/instance=$K8S_RELEASE"

  port_forward "$K8S_FULLNAME-api-gateway" "$K8S_GATEWAY_LOCAL_PORT" 8080
  port_forward "$K8S_FULLNAME-ai-incident-assistant" "$K8S_AI_LOCAL_PORT" 8090
  wait_for_http "api-gateway health" "http://127.0.0.1:$K8S_GATEWAY_LOCAL_PORT/actuator/health"
  wait_for_http "ai-incident-assistant health" "http://127.0.0.1:$K8S_AI_LOCAL_PORT/health"
  echo "Kubernetes smoke passed for release '$K8S_RELEASE' in namespace '$K8S_NAMESPACE'."
else
  if ! kubectl get namespace "$K8S_NAMESPACE" >/dev/null 2>&1; then
    fail "namespace '$K8S_NAMESPACE' does not exist for server-side dry-run; create it or run with K8S_SMOKE_APPLY=true"
  fi

  # shellcheck disable=SC2086
  helm_cmd template "$K8S_RELEASE" "$CHART_PATH" \
    --namespace "$K8S_NAMESPACE" \
    $HELM_EXTRA_ARGS \
    | kubectl apply --namespace "$K8S_NAMESPACE" --dry-run=server -f - >/dev/null
  echo "Kubernetes smoke server-side dry-run passed for context '$K8S_CONTEXT'."
fi
