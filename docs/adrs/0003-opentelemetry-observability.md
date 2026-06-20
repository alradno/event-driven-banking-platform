# ADR 0003: OpenTelemetry First Observability

## Status

Accepted

## Context

The platform must prove end-to-end traceability through gateway, services, events, audit records, and incident analysis.

## Decision

Use OpenTelemetry-compatible tracing and Micrometer metrics in each service. Correlation IDs and trace IDs are propagated through HTTP headers and event envelopes. Java services export spans to the local OpenTelemetry collector over OTLP, and payment request spans are tagged with `banking.correlation_id` and `banking.trace_id` so the runtime contract can prove collector-visible traceability. Prometheus and Grafana are the default local observability stack.

## Consequences

- Incidents can be investigated using traces, metrics, logs, and events.
- E2E tests can verify exported trace tags without relying on paid tracing services.
- The AI incident assistant can cite trace IDs and runbooks.
- Services need consistent logging, headers, and event metadata conventions.
