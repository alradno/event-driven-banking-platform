#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/../lib.sh"

set +e
curl -s -o /dev/null -w "unauthorized status=%{http_code}\n" "$GATEWAY_URL/payments"
set -e

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"metrics":{"gateway.auth_failures":12},"runbooks":["runbooks/auth-failure.md"]}'
echo
