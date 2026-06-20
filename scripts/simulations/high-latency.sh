#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/../lib.sh"

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"logs":[{"service":"payment-service","message":"database timeout while loading account","traceId":"trace-latency-001"}],"runbooks":["runbooks/high-latency.md","runbooks/db-connectivity.md"]}'
echo
