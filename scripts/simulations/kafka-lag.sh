#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/../lib.sh"

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"kafka":{"max_lag":250},"runbooks":["runbooks/kafka-lag.md"],"traces":["trace-kafka-lag"]}'
echo
