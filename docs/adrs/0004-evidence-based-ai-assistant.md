# ADR 0004: Evidence-Based AI Incident Assistant

## Status

Accepted

## Context

The assistant must be useful in a portfolio demo without API keys while avoiding unsupported incident claims.

## Decision

Implement a deterministic rule-based FastAPI service first. It accepts structured incident evidence, metrics snapshots, logs, trace IDs, Kafka lag/DLQ indicators, and runbook references. Optional Ollama or OpenAI-compatible providers may be added later, but the service must work without them.

## Consequences

- Fresh clone works without paid services.
- Tests can prove that likely causes require evidence.
- The assistant can be extended with model providers without changing the incident contract.
