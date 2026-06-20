# Event Compatibility

All events use the envelope documented in `SPEC.md`. Consumers must ignore unknown payload fields and reject only unknown event envelope versions.

## Topics

| Topic | Producer | Consumers |
| --- | --- | --- |
| `bank.customer.events` | customer-service | audit-service |
| `bank.account.events` | account-service | audit-service |
| `bank.payment.events` | payment-service | audit-service, notification-service |
| `bank.audit.events` | audit-service | ai-incident-assistant demos |
| `bank.security.events` | api-gateway | audit-service |
| `bank.notification.events` | notification-service | audit-service |

## Retry and DLQ

Notification failures are retried by `notification-service` and then sent to `bank.payment.events.notification-service.dlq`. The DLQ payload preserves the original envelope and headers so that a replay can keep correlation IDs intact.
