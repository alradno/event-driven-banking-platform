#!/usr/bin/env sh
set -eu

. "$(dirname "$0")/lib.sh"

AI_URL="${AI_URL:-http://localhost:18090}"
DLQ_TOPIC="bank.payment.events.notification-service.dlq"
RATE_FILE=""

fail() {
  echo "E2E test failed: $*" >&2
  exit 1
}

cleanup() {
  if [ -n "$RATE_FILE" ]; then
    rm -f "$RATE_FILE"
  fi
  if [ -n "${SRE_TOKEN:-}" ]; then
    curl -sS -o /dev/null -X POST "$GATEWAY_URL/demo/notification-failure" \
      -H "Authorization: Bearer $SRE_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"enabled": false}' || true
  fi
}
trap cleanup EXIT

http_status() {
  curl -sS -o /dev/null -w "%{http_code}" "$@"
}

json_assert() {
  python3 -c '
import json
import sys

expr = sys.argv[1]
data = json.load(sys.stdin)
if not eval(expr, {"__builtins__": {}, "any": any, "all": all, "len": len}, {"data": data}):
    raise SystemExit(f"assertion failed: {expr}; payload={data!r}")
' "$1"
}

psql_scalar() {
  db="$1"
  sql="$2"
  compose_exec postgres psql -U banking -d "$db" -tA -c "$sql" | tr -d '\r' | tail -n 1
}

trace_exported() {
  compose_logs otel-collector 20000 2>/dev/null | grep -Fq "banking.trace_id: Str($TRACE_ID)"
}

curl -fsS "$GATEWAY_URL/actuator/health" >/dev/null
curl -fsS "$AI_URL/health" >/dev/null

ALICE_TOKEN="$(token_for alice)"
BOB_TOKEN="$(token_for bob)"
SRE_TOKEN="$(token_for sre)"
AUDITOR_TOKEN="$(token_for auditor)"

curl -fsS -X POST "$GATEWAY_URL/demo/notification-failure" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' >/dev/null

status="$(http_status "$GATEWAY_URL/payments")"
[ "$status" = "401" ] || fail "expected unauthenticated /payments to return 401, got $status"

status="$(http_status -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')"
[ "$status" = "403" ] || fail "expected SRE payment creation to return 403, got $status"

CUSTOMER="$(curl -fsS "$GATEWAY_URL/customers/me" \
  -H "Authorization: Bearer $ALICE_TOKEN")"
printf '%s' "$CUSTOMER" | json_assert 'data["id"] == "11111111-1111-1111-1111-111111111111"'

ACCOUNTS="$(curl -fsS "$GATEWAY_URL/accounts" \
  -H "Authorization: Bearer $ALICE_TOKEN")"
printf '%s' "$ACCOUNTS" | json_assert 'any(item["id"] == "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1" for item in data)'
printf '%s' "$ACCOUNTS" | json_assert 'all(item["customerId"] == "11111111-1111-1111-1111-111111111111" for item in data)'

status="$(http_status "$GATEWAY_URL/accounts/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1" \
  -H "Authorization: Bearer $ALICE_TOKEN")"
[ "$status" = "403" ] || fail "expected Alice reading Bob account to return 403, got $status"

CORRELATION_ID="e2e-$(date +%s)-$$"
TRACE_ID="trace-$CORRELATION_ID"
IDEMPOTENCY_KEY="e2e-key-$CORRELATION_ID"
PAYLOAD='{
  "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
  "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
  "currency": "CHF",
  "amountMinor": 321,
  "description": "e2e transfer"
}'

PAYMENT_OFFSET_BEFORE="$(kafka_offset bank.payment.events)"
NOTIFICATION_OFFSET_BEFORE="$(kafka_offset bank.notification.events)"

PAYMENT="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: $TRACE_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$PAYLOAD")"
PAYMENT_ID="$(printf '%s' "$PAYMENT" | json_value id)"
PAYMENT_STATUS="$(printf '%s' "$PAYMENT" | json_value status)"
[ "$PAYMENT_STATUS" = "COMPLETED" ] || fail "expected completed payment, got $PAYMENT_STATUS"

REPLAY="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: $TRACE_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$PAYLOAD")"
REPLAY_ID="$(printf '%s' "$REPLAY" | json_value id)"
[ "$PAYMENT_ID" = "$REPLAY_ID" ] || fail "idempotency replay changed payment id $PAYMENT_ID != $REPLAY_ID"

MISMATCH_PAYLOAD='{
  "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
  "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
  "currency": "CHF",
  "amountMinor": 322,
  "description": "e2e transfer changed body"
}'
status="$(http_status -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -H "X-Trace-Id: $TRACE_ID" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$MISMATCH_PAYLOAD")"
[ "$status" = "409" ] || fail "expected idempotency key reuse with changed body to return 409, got $status"

wait_for "payment outbox publish" \
  "[ \"\$(psql_scalar payment_db \"select count(*) from outbox_events where aggregate_id = '$PAYMENT_ID' and event_type = 'payment.completed' and published_at is not null;\")\" -ge 1 ]"

PAYMENT_OFFSET_AFTER="$(kafka_offset bank.payment.events)"
[ "$PAYMENT_OFFSET_AFTER" -gt "$PAYMENT_OFFSET_BEFORE" ] \
  || fail "payment topic offset did not increase ($PAYMENT_OFFSET_BEFORE -> $PAYMENT_OFFSET_AFTER)"

wait_for "notification event publish" \
  "[ \"\$(kafka_offset bank.notification.events)\" -gt \"$NOTIFICATION_OFFSET_BEFORE\" ]"

wait_for "audit records for payment correlation" \
  "[ \"\$(psql_scalar audit_db \"select count(*) from audit_records where correlation_id = '$CORRELATION_ID' and event_type = 'payment.completed';\")\" -ge 1 ]"

AUDITS="$(curl -fsS "$GATEWAY_URL/audits?correlationId=$CORRELATION_ID" \
  -H "Authorization: Bearer $AUDITOR_TOKEN")"
printf '%s' "$AUDITS" | json_assert 'any(item["eventType"] == "payment.completed" and item["traceId"].startswith("trace-e2e-") for item in data)'

wait_for "OpenTelemetry trace export" \
  "trace_exported" 60 2 \
  || fail "OpenTelemetry collector logs did not include banking.trace_id=$TRACE_ID"

PROMETHEUS="$(curl -fsS "$GATEWAY_URL/actuator/prometheus" \
  -H "Authorization: Bearer $SRE_TOKEN")"
printf '%s' "$PROMETHEUS" | grep -q 'http_server_requests_seconds' \
  || fail "gateway prometheus metrics did not include http_server_requests_seconds"

AI_REPORT="$(curl -fsS -X POST "$GATEWAY_URL/ai/incidents/analyze" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"metrics":{"payment.error_rate":0.31},"kafka":{"dlq_count":2},"traces":["trace-e2e-ai"],"runbooks":["runbooks/payment-failure.md","runbooks/kafka-lag.md"]}')"
printf '%s' "$AI_REPORT" | json_assert 'data["severity"] == "high"'
printf '%s' "$AI_REPORT" | json_assert '"payment-service" in data["affectedServices"] and "notification-service" in data["affectedServices"]'
printf '%s' "$AI_REPORT" | json_assert 'data["evidence"] and data["likelyCauses"] and "trace-e2e-ai" in data["traceIds"] and data["runbooks"]'

INSUFFICIENT_CORRELATION_ID="insufficient-$CORRELATION_ID"
INSUFFICIENT_PAYMENT="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $INSUFFICIENT_CORRELATION_ID" \
  -H "X-Trace-Id: trace-$INSUFFICIENT_CORRELATION_ID" \
  -H "Idempotency-Key: e2e-insufficient-key-$CORRELATION_ID" \
  -d '{
    "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
    "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
    "currency": "CHF",
    "amountMinor": 999999999,
    "description": "e2e insufficient funds"
  }')"
printf '%s' "$INSUFFICIENT_PAYMENT" | json_assert 'data["status"] == "REJECTED" and data["rejectionReason"] == "INSUFFICIENT_FUNDS"'

FROZEN_CORRELATION_ID="frozen-$CORRELATION_ID"
FROZEN_PAYMENT="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $FROZEN_CORRELATION_ID" \
  -H "X-Trace-Id: trace-$FROZEN_CORRELATION_ID" \
  -H "Idempotency-Key: e2e-frozen-key-$CORRELATION_ID" \
  -d '{
    "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2",
    "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
    "currency": "EUR",
    "amountMinor": 1,
    "description": "e2e frozen source"
  }')"
printf '%s' "$FROZEN_PAYMENT" | json_assert 'data["status"] == "REJECTED" and data["rejectionReason"] == "ACCOUNT_NOT_ACTIVE"'

OWNERSHIP_CORRELATION_ID="ownership-$CORRELATION_ID"
OWNERSHIP_PAYMENT="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $OWNERSHIP_CORRELATION_ID" \
  -H "X-Trace-Id: trace-$OWNERSHIP_CORRELATION_ID" \
  -H "Idempotency-Key: e2e-ownership-key-$CORRELATION_ID" \
  -d '{
    "sourceAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
    "targetAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
    "currency": "CHF",
    "amountMinor": 1,
    "description": "e2e ownership mismatch"
  }')"
printf '%s' "$OWNERSHIP_PAYMENT" | json_assert 'data["status"] == "REJECTED" and data["rejectionReason"] == "OWNERSHIP_MISMATCH"'

wait_for "audit records for rejected payment correlations" \
  "[ \"\$(psql_scalar audit_db \"select count(*) from audit_records where correlation_id in ('$INSUFFICIENT_CORRELATION_ID', '$FROZEN_CORRELATION_ID', '$OWNERSHIP_CORRELATION_ID') and event_type = 'payment.rejected';\")\" -ge 3 ]"

EXPECTED_NOTIFICATION_OFFSET_AFTER_REJECTIONS=$((NOTIFICATION_OFFSET_BEFORE + 4))
wait_for "notification events for completed and rejected payments" \
  "[ \"\$(kafka_offset bank.notification.events)\" -ge \"$EXPECTED_NOTIFICATION_OFFSET_AFTER_REJECTIONS\" ]" 30 1 \
  || fail "notification events for completed/rejected payments did not reach offset $EXPECTED_NOTIFICATION_OFFSET_AFTER_REJECTIONS"

DLQ_OFFSET_BEFORE="$(kafka_offset "$DLQ_TOPIC")"
DLQ_CORRELATION_ID="dlq-$CORRELATION_ID"
DLQ_TRACE_ID="trace-$DLQ_CORRELATION_ID"
DLQ_IDEMPOTENCY_KEY="e2e-dlq-key-$CORRELATION_ID"
DLQ_PAYLOAD='{
  "sourceAccountId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1",
  "targetAccountId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
  "currency": "CHF",
  "amountMinor": 17,
  "description": "e2e dlq transfer"
}'

curl -fsS -X POST "$GATEWAY_URL/demo/notification-failure" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}' >/dev/null

DLQ_PAYMENT="$(curl -fsS -X POST "$GATEWAY_URL/payments" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $DLQ_CORRELATION_ID" \
  -H "X-Trace-Id: $DLQ_TRACE_ID" \
  -H "Idempotency-Key: $DLQ_IDEMPOTENCY_KEY" \
  -d "$DLQ_PAYLOAD")"
DLQ_PAYMENT_STATUS="$(printf '%s' "$DLQ_PAYMENT" | json_value status)"
[ "$DLQ_PAYMENT_STATUS" = "COMPLETED" ] || fail "expected completed DLQ demo payment, got $DLQ_PAYMENT_STATUS"

wait_for "notification DLQ publish" \
  "[ \"\$(kafka_offset '$DLQ_TOPIC')\" -gt \"$DLQ_OFFSET_BEFORE\" ]" 45 1 \
  || fail "notification DLQ offset did not increase"

curl -fsS -X POST "$GATEWAY_URL/demo/notification-failure" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' >/dev/null

REPLAY_NOTIFICATION_OFFSET_BEFORE="$(kafka_offset bank.notification.events)"
REPLAY_RESPONSE="$(curl -fsS -X POST "$GATEWAY_URL/demo/notification-dlq/replay" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"correlationId\":\"$DLQ_CORRELATION_ID\",\"maxRecords\":1}")"
printf '%s' "$REPLAY_RESPONSE" | json_assert 'data["replayed"] >= 1 and data["dlqTopic"].endswith(".dlq")'

wait_for "notification DLQ replay publication" \
  "[ \"\$(kafka_offset bank.notification.events)\" -gt \"$REPLAY_NOTIFICATION_OFFSET_BEFORE\" ]" 30 1 \
  || fail "notification replay did not produce a notification event"

NO_MATCH_NOTIFICATION_OFFSET_BEFORE="$(kafka_offset bank.notification.events)"
NO_MATCH_REPLAY_RESPONSE="$(curl -fsS -X POST "$GATEWAY_URL/demo/notification-dlq/replay" \
  -H "Authorization: Bearer $SRE_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"correlationId\":\"missing-$CORRELATION_ID\",\"maxRecords\":3}")"
printf '%s' "$NO_MATCH_REPLAY_RESPONSE" | json_assert 'data["replayed"] == 0'
sleep 2
NO_MATCH_NOTIFICATION_OFFSET_AFTER="$(kafka_offset bank.notification.events)"
[ "$NO_MATCH_NOTIFICATION_OFFSET_AFTER" = "$NO_MATCH_NOTIFICATION_OFFSET_BEFORE" ] \
  || fail "no-match DLQ replay unexpectedly changed notification offset ($NO_MATCH_NOTIFICATION_OFFSET_BEFORE -> $NO_MATCH_NOTIFICATION_OFFSET_AFTER)"

RATE_FILE="$(mktemp)"
i=0
while [ "$i" -lt 90 ]; do
  (
    curl -sS -o /dev/null -w "%{http_code}\n" "$GATEWAY_URL/accounts" \
      -H "Authorization: Bearer $BOB_TOKEN" >> "$RATE_FILE" || true
  ) &
  i=$((i + 1))
done
wait
if ! grep -q '^429$' "$RATE_FILE"; then
  fail "expected at least one 429 from Redis rate limiting; statuses: $(sort "$RATE_FILE" | uniq -c | tr '\n' ' ')"
fi

wait_for "security audit records for 401, 403, and 429" \
  "[ \"\$(psql_scalar audit_db \"select count(distinct event_type) from audit_records where event_type in ('security.login_failed', 'security.access_denied', 'security.rate_limit_exceeded');\")\" -ge 3 ]" 45 1 \
  || fail "security events for login_failed/access_denied/rate_limit_exceeded were not audited"

echo "E2E test passed. Payment $PAYMENT_ID correlation=$CORRELATION_ID kafkaOffsets=$PAYMENT_OFFSET_BEFORE->$PAYMENT_OFFSET_AFTER traceExport=ok negativePayments=ok dlqReplay=ok replaySafety=ok securityAudit=ok."
