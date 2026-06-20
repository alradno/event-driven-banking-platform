# Runbook: Payment Failure

1. Search by `X-Correlation-Id` in gateway, payment-service, account-service, audit-service, and Kafka event payloads.
2. Check payment status and rejection reason.
3. Confirm source account status, ownership, currency, and balance.
4. Inspect outbox rows that are unpublished for more than two minutes.
5. Replay only after confirming idempotency key behavior and account movement.
