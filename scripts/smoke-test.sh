#!/usr/bin/env sh
set -eu

. "$(dirname "$0")/lib.sh"

curl -fsS "$GATEWAY_URL/actuator/health" >/dev/null
curl -fsS "${AI_URL:-http://localhost:18090}/health" >/dev/null

TOKEN="$(token_for alice)"
CORRELATION_ID="smoke-$(date +%s)"
IDEMPOTENCY_KEY="smoke-key-$CORRELATION_ID"
PAYLOAD='{
  "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
  "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
  "currency": "CHF",
  "amountMinor": 500,
  "description": "smoke transfer"
}'

FIRST="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: trace-$CORRELATION_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$PAYLOAD")"

SECOND="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: trace-$CORRELATION_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$PAYLOAD")"

FIRST_ID="$(printf '%s' "$FIRST" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
SECOND_ID="$(printf '%s' "$SECOND" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"

if [ "$FIRST_ID" != "$SECOND_ID" ]; then
  echo "Idempotency smoke test failed: $FIRST_ID != $SECOND_ID" >&2
  exit 1
fi

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{"metrics":{"payment.error_rate":0.25},"kafka":{"dlq_count":1},"traces":["trace-smoke"]}' \
  | python3 -c 'import json,sys; data=json.load(sys.stdin); assert data["evidence"]; assert data["likelyCauses"]'

echo "Smoke test passed. Payment $FIRST_ID was idempotent."
