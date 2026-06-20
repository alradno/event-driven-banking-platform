# Event-Driven Banking Platform Specification

## Purpose

`event-driven-banking-platform` is a portfolio monorepo for a production-style banking system. It combines secure API access, service-owned data, Kafka-driven workflows, auditability, observability, CI/CD, and an evidence-based AI incident assistant that can run without paid API keys.

## Architecture Scope

The first runnable product is a Docker Compose MVP. Kubernetes and Helm packaging is added after the Compose system is operational and tested; the chart targets the application services and expects backing infrastructure to be supplied by the cluster environment.

### Core Runtime

- Java 21, Spring Boot 3, Maven monorepo.
- Python FastAPI service for the AI incident assistant.
- Spring Cloud Gateway as the only public entry point.
- Keycloak for OIDC/OAuth2/JWT demo identity.
- Redis-backed gateway rate limiting.
- Kafka-compatible broker for asynchronous workflows.
- PostgreSQL with service-owned databases.
- Transactional outbox for payment domain events.
- OpenTelemetry, Prometheus, Grafana-compatible metrics and JSON logs.

### Services

- `api-gateway`: public ingress, route security, role/scope authorization, rate limits, standard errors, security headers, and correlation/trace propagation.
- `customer-service`: manages customers, status, and risk levels.
- `account-service`: manages CHF/EUR/USD accounts, minor-unit balances, account state, and strict ownership checks.
- `payment-service`: creates, queries, and cancels transfers; enforces idempotency, ownership, frozen-account, and insufficient-funds rules; emits outbox-backed events.
- `audit-service`: stores immutable business and security audit records without sensitive values.
- `notification-service`: consumes payment outcomes with retry/DLQ behavior and controllable demo failures.
- `ai-incident-assistant`: analyzes logs, metrics, traces, Kafka lag/DLQ data, and runbooks; returns severity, summary, affected services, likely causes, confidence, evidence, trace IDs, and suggested checks.

## Security Requirements

Demo roles:

- `CUSTOMER`
- `SUPPORT_AGENT`
- `BACKOFFICE_OPERATOR`
- `AUDITOR`
- `SRE`
- `ADMIN`

The gateway must enforce JWT authentication, roles/scopes, 401 for missing/invalid authentication, 403 for insufficient authorization, 429 for rate limiting, and correlation-aware standard error responses. Services must not trust caller-provided ownership data unless it is derived from authenticated claims or an internal service contract.

Sensitive values must never be logged, stored in audit payloads, or committed. The repository must provide `.env.example` only.

## Event Model

All domain, audit, security, and notification events use a versioned envelope:

- `eventId`
- `eventType`
- `eventVersion`
- `occurredAt`
- `producer`
- `correlationId`
- `causationId`
- `traceId`
- `subject`
- `payload`

Required event topics:

- `bank.customer.events`
- `bank.account.events`
- `bank.payment.events`
- `bank.audit.events`
- `bank.security.events`
- `bank.notification.events`
- retry and DLQ topics per consumer group where useful for demos.

Required payment event types:

- `payment.created`
- `payment.validated`
- `payment.processing`
- `payment.completed`
- `payment.rejected`
- `payment.failed`

Required security event types:

- `security.login_failed`
- `security.access_denied`
- `security.rate_limit_exceeded`

## Payment State Machine

Payments move through:

`CREATED -> VALIDATED -> PROCESSING -> COMPLETED`

Terminal alternatives:

- `REJECTED`: business rule failure such as frozen account, closed account, ownership mismatch, or insufficient funds.
- `FAILED`: technical processing failure.
- `CANCELLED`: accepted cancellation before processing begins.

Duplicate idempotency keys for the same authenticated customer and request body must return the same payment and must not duplicate balance movement or events. Reusing an idempotency key with a different request body must be rejected as a conflict.

## Observability Requirements

Every service must expose health/readiness and metrics. Correlation IDs and trace IDs must flow from gateway to services to events and audit records.

Required dashboard domains:

- Gateway latency, errors, authentication failures, authorization denials, and rate limits.
- Payment throughput, failures, state durations, and stuck payments.
- Kafka lag, retry, and DLQ volume.
- JVM and resource usage.

Required alert domains:

- High error rate.
- Stuck payments.
- Kafka lag.
- DLQ growth.
- Authentication spikes.
- Database connectivity failures.

Required runbooks:

- Payment failure.
- Kafka lag.
- Authentication failure.
- High latency.
- Database connectivity.

## AI Incident Assistant Requirements

The AI service must work rule-based without external keys. Optional providers may be added later for Ollama or OpenAI-compatible APIs, but the service must not require them. It must cite evidence and runbooks and must not claim a likely cause without supporting evidence.

Output fields:

- `severity`
- `summary`
- `affectedServices`
- `likelyCauses`
- `confidence`
- `evidence`
- `traceIds`
- `checks`
- `runbooks`

## Developer Experience Requirements

Required Make targets:

- `up`
- `down`
- `seed`
- `test`
- `integration-test`
- `demo`
- `logs`
- `simulate-payment-stuck`
- `simulate-notification-failure`
- `simulate-auth-failure`
- `simulate-high-latency`
- `simulate-kafka-lag`

The repo must include OpenAPI, Postman collection, seed data, deterministic demos, unit tests, integration/security/E2E tests, CI, image builds, and Compose smoke testing.
The `e2e-test` target should run against an already-started Compose stack and prove the runtime path across gateway, services, Kafka, audit, notification, metrics, and AI evidence.

## Done Criteria

- Fresh clone runs without paid services or API keys.
- Tests and CI pass.
- Duplicate idempotency keys cannot duplicate payments.
- Each payment is traceable end to end via correlation IDs, trace IDs, events, and audit records.
- Retry/DLQ and all five simulations work.
- AI reports cite real evidence and runbooks.
- Repository is polished, documented, and runnable.
