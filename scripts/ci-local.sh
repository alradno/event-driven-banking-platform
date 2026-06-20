#!/usr/bin/env sh
set -eu

COMPOSE="${COMPOSE:-podman compose}"
COMPOSE_CMD="${COMPOSE_CMD:-$COMPOSE}"
MVNW="${MVNW:-./mvnw}"
PYTHON="${PYTHON:-python3}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:18080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:18089}"
AI_URL="${AI_URL:-http://localhost:18090}"
export COMPOSE_CMD GATEWAY_URL KEYCLOAK_URL AI_URL

compose_diagnostics() {
  echo "Compose status:" >&2
  # shellcheck disable=SC2086
  $COMPOSE ps >&2 || true

  for service in keycloak api-gateway ai-incident-assistant customer-service account-service payment-service audit-service notification-service; do
    echo "---- $service logs ----" >&2
    # shellcheck disable=SC2086
    $COMPOSE logs --tail=120 "$service" >&2 || true
  done
}

cleanup() {
  status="$?"
  if [ "$status" -ne 0 ]; then
    compose_diagnostics
  fi

  # shellcheck disable=SC2086
  $COMPOSE down -v >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

wait_http() {
  description="$1"
  url="$2"
  attempts="${3:-90}"
  delay="${4:-2}"
  i=1

  while [ "$i" -le "$attempts" ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "$description is ready."
      return 0
    fi
    sleep "$delay"
    i=$((i + 1))
  done

  echo "Timed out waiting for $description at $url" >&2
  return 1
}

# Start from a clean Compose project so local leftovers do not mask CI issues.
# shellcheck disable=SC2086
$COMPOSE down -v >/dev/null 2>&1 || true

./scripts/ci-scan.sh
"$MVNW" test
cd ai-incident-assistant && "$PYTHON" -m unittest discover -s tests
cd ..

# shellcheck disable=SC2086
$COMPOSE build
# shellcheck disable=SC2086
$COMPOSE up -d
wait_http "Keycloak realm" "$KEYCLOAK_URL/realms/banking/.well-known/openid-configuration"
wait_http "API gateway" "$GATEWAY_URL/actuator/health"
wait_http "AI incident assistant" "$AI_URL/health" 60
./scripts/smoke-test.sh
./scripts/e2e-test.sh

echo "Local CI parity passed: scans, tests, image build, Compose smoke, and E2E completed."
