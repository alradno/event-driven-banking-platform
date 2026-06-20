#!/usr/bin/env sh
set -eu

. "$(dirname "$0")/lib.sh"

AI_URL="${AI_URL:-http://localhost:18090}"
COMPOSE_CMD="${COMPOSE_CMD:-}"
if [ -z "$COMPOSE_CMD" ]; then
  if command -v podman-compose >/dev/null 2>&1; then
    COMPOSE_CMD="podman-compose --no-ansi"
  elif command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
    COMPOSE_CMD="podman compose --no-ansi"
  else
    COMPOSE_CMD="docker compose"
  fi
fi

fail() {
  echo "E2E test failed: $*" >&2
  exit 1
}

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

json_value() {
  python3 -c '
import json
import sys

path = sys.argv[1].split(".")
value = json.load(sys.stdin)
for key in path:
    if key:
        value = value[key]
print(value)
' "$1"
}

compose_exec() {
  service="$1"
  shift
  # Intentionally unquoted so callers can pass commands such as "docker compose".
  # shellcheck disable=SC2086
  $COMPOSE_CMD exec -T "$service" "$@"
}

psql_scalar() {
  db="$1"
  sql="$2"
  compose_exec postgres psql -U banking -d "$db" -tA -c "$sql" | tr -d '\r' | tail -n 1
}

kafka_offset() {
  topic="$1"
  compose_exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic "$topic" \
    | awk -F: -v topic="$topic" '$1 == topic { total += $3 } END { print total + 0 }'
}

wait_for() {
  description="$1"
  command="$2"
  attempts="${3:-30}"
  delay="${4:-1}"
  i=1
  while [ "$i" -le "$attempts" ]; do
    if eval "$command" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay"
    i=$((i + 1))
  done
  fail "$description did not become true"
}

curl -fsS "$GATEWAY_URL/actuator/health" >/dev/null
curl -fsS "$AI_URL/health" >/dev/null

ALICE_TOKEN="$(token_for alice)"
BOB_TOKEN="$(token_for bob)"
SRE_TOKEN="$(token_for sre)"
AUDITOR_TOKEN="$(token_for auditor)"

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

RATE_FILE="$(mktemp)"
trap 'rm -f "$RATE_FILE"' EXIT
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

echo "E2E test passed. Payment $PAYMENT_ID correlation=$CORRELATION_ID kafkaOffsets=$PAYMENT_OFFSET_BEFORE->$PAYMENT_OFFSET_AFTER."
