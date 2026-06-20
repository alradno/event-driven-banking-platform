# ADR 0001: Kafka Events and Transactional Outbox

## Status

Accepted

## Context

Payments must be traceable and resilient. Directly publishing Kafka events inside request handlers risks losing events when database commits and broker sends diverge.

## Decision

Domain services publish versioned events to Kafka. `payment-service` persists payment state and an outbox row in the same PostgreSQL transaction. A background publisher sends unpublished outbox rows to Kafka and marks them published after broker acknowledgement.

## Consequences

- Payment state and event intent are atomic.
- Consumers can be retried independently.
- The system can demonstrate DLQ and replay behavior.
- Additional outbox cleanup and monitoring are required.
