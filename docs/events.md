# Event Compatibility

All events use the envelope documented in `SPEC.md`. The current envelope version is `1`. Consumers must ignore unknown payload fields and reject unknown event envelope versions; automated v1 JSON fixtures in `banking-common` protect this wire shape.

## Topics

| Topic | Producer | Consumers |
| --- | --- | --- |
| `bank.customer.events` | customer-service | audit-service |
| `bank.account.events` | account-service | audit-service |
| `bank.payment.events` | payment-service | audit-service, notification-service |
| `bank.audit.events` | audit-service | ai-incident-assistant demos |
| `bank.security.events` | api-gateway | audit-service |
| `bank.notification.events` | notification-service | audit-service |

## Security Events

The gateway publishes sanitized security envelopes to `bank.security.events`:

| Event | Trigger |
| --- | --- |
| `security.login_failed` | Missing or invalid bearer token returns `401`. |
| `security.access_denied` | Authenticated principal lacks the required route role or scope and receives `403`. |
| `security.rate_limit_exceeded` | Redis-backed gateway rate limiting returns `429`. |

Security event payloads include request method, path, status, reason, and remote address only. They intentionally exclude bearer tokens and other sensitive values before the audit service stores the event.

## Retry and DLQ

Notification failures are retried by `notification-service` and then sent to `bank.payment.events.notification-service.dlq`. The DLQ value preserves the original event envelope, including correlation and trace IDs.

SRE and ADMIN users can replay a bounded set of matching DLQ records through the gateway:

```http
POST /demo/notification-dlq/replay
Content-Type: application/json

{"correlationId":"<correlation-id>","maxRecords":1}
```

The replay service scans the notification DLQ from the beginning, filters by envelope `correlationId`, republishes matching raw envelopes to `bank.payment.events`, and returns the source topic, replay topic, scanned count, and replayed count.
