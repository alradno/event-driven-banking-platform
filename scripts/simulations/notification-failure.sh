#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/../lib.sh"

curl -fsS -X POST "$GATEWAY_URL/demo/notification-failure" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
echo

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"kafka":{"dlq_count":3},"runbooks":["runbooks/kafka-lag.md"],"traces":["trace-notification-failure"]}'
echo
