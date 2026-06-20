#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/../lib.sh"

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"metrics":{"payment.stuck.count":4},"traces":["trace-stuck-001"],"runbooks":["runbooks/payment-failure.md"]}'
echo
