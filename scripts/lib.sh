#!/usr/bin/env sh
set -eu

GATEWAY_URL="${GATEWAY_URL:-http://localhost:18080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:18089}"
CLIENT_ID="${CLIENT_ID:-banking-gateway}"
PASSWORD="${PASSWORD:-password}"

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
