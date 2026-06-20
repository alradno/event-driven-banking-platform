#!/usr/bin/env sh
set -eu

GATEWAY_URL="${GATEWAY_URL:-http://localhost:18080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:18089}"
CLIENT_ID="${CLIENT_ID:-banking-gateway}"
PASSWORD="${PASSWORD:-password}"
COMPOSE_CMD="${COMPOSE_CMD:-}"
if [ -z "$COMPOSE_CMD" ]; then
  if command -v podman-compose >/dev/null 2>&1; then
    COMPOSE_CMD="podman-compose --no-ansi"
  elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE_CMD="podman compose --no-ansi"
  else
    COMPOSE_CMD="docker compose"
  fi
fi

token_for() {
  username="$1"
  curl -fsS -X POST "$KEYCLOAK_URL/realms/banking/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=$CLIENT_ID" \
    -d "username=$username" \
    -d "password=$PASSWORD" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

json_field() {
  field="$1"
  python3 -c "import json,sys; print(json.load(sys.stdin).get('$field', ''))"
}

json_value() {
  path="$1"
  python3 -c '
import json
import sys

value = json.load(sys.stdin)
for key in sys.argv[1].split("."):
    if key:
        value = value[key]
print(value)
' "$path"
}

compose_exec() {
  service="$1"
  shift
  # Intentionally unquoted so callers can pass commands such as "docker compose".
  # shellcheck disable=SC2086
  $COMPOSE_CMD exec -T "$service" "$@"
}

compose_logs() {
  service="$1"
  tail="${2:-5000}"
  # Intentionally unquoted so callers can pass commands such as "docker compose".
  # shellcheck disable=SC2086
  $COMPOSE_CMD logs --tail="$tail" "$service" 2>&1
}

kafka_offset() {
  topic="$1"
  compose_exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic "$topic" 2>/dev/null \
    | awk -F: -v topic="$topic" '$1 == topic { total += $3 } END { print total + 0 }'
}

wait_for() {
  description="$1"
  command="$2"
  attempts="${3:-30}"
  delay="${4:-1}"
  i=1
  while [ "$i" -le "$attempts" ]; do
    if eval "$command" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay"
    i=$((i + 1))
  done
  echo "Timed out waiting for $description" >&2
  return 1
}
