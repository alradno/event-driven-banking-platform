#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/../lib.sh"

DLQ_TOPIC="bank.payment.events.notification-service.dlq"
SRE_TOKEN="$(token_for sre)"
ALICE_TOKEN="$(token_for alice)"
CORRELATION_ID="sim-notification-failure-$(date +%s)-$$"
TRACE_ID="trace-$CORRELATION_ID"
IDEMPOTENCY_KEY="sim-notification-failure-key-$CORRELATION_ID"
DLQ_OFFSET_BEFORE="$(kafka_offset "$DLQ_TOPIC")"

cleanup() {
  curl -sS -o /dev/null -X POST "$GATEWAY_URL/demo/notification-failure" \
    -H "Authorization: Bearer $SRE_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"enabled": false}' || true
}
trap cleanup EXIT

curl -fsS -X POST "$GATEWAY_URL/demo/notification-failure" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
echo

curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: $TRACE_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
    "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
    "currency": "CHF",
    "amountMinor": 23,
    "description": "notification failure simulation"
  }' >/dev/null

wait_for "notification DLQ event" \
  "[ \"\$(kafka_offset '$DLQ_TOPIC')\" -gt \"$DLQ_OFFSET_BEFORE\" ]" 45 1

curl -fsS -X POST "$GATEWAY_URL/demo/notification-failure" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' >/dev/null

REPLAY_NOTIFICATION_OFFSET_BEFORE="$(kafka_offset bank.notification.events)"
curl -fsS -X POST "$GATEWAY_URL/demo/notification-dlq/replay" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"correlationId\":\"$CORRELATION_ID\",\"maxRecords\":1}" \
  | python3 -c 'import json,sys; data=json.load(sys.stdin); assert data["replayed"] >= 1, data; print(json.dumps(data))'

wait_for "notification replay event" \
  "[ \"\$(kafka_offset bank.notification.events)\" -gt \"$REPLAY_NOTIFICATION_OFFSET_BEFORE\" ]" 30 1

curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"kafka\":{\"dlq_count\":1},\"runbooks\":[\"runbooks/kafka-lag.md\"],\"traces\":[\"$TRACE_ID\"]}"
echo
