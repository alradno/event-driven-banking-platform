#!/usr/bin/env sh
set -eu

. "$(dirname "$0")/lib.sh"

TOKEN="$(token_for alice)"
CORRELATION_ID="demo-$(date +%s)"
IDEMPOTENCY_KEY="demo-key-$CORRELATION_ID"

echo "Creating CHF payment with correlation $CORRELATION_ID"
curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: trace-$CORRELATION_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
    "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
    "currency": "CHF",
    "amountMinor": 1250,
    "description": "portfolio demo transfer"
  }'

echo
echo "Replaying the same idempotency key. The payment ID must remain the same."
curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: trace-$CORRELATION_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
    "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
    "currency": "CHF",
    "amountMinor": 1250,
    "description": "portfolio demo transfer"
  }'

echo
echo "Ask the AI assistant to analyze an evidence-backed incident."
curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $(token_for sre)" \
  -H "Content-Type: application/json" \
  -d '{
    "metrics": {"payment.error_rate": 0.35},
    "kafka": {"dlq_count": 2},
    "traces": ["trace-demo-001"],
    "runbooks": ["runbooks/payment-failure.md", "runbooks/kafka-lag.md"]
  }'
echo
