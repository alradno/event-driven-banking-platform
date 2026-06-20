# Implementation Plan

## Phase 0 - Repository Contract

- Create `SPEC.md`, `PLAN.md`, `STATUS.md`, ADRs, and initial README.
- Add `.gitignore`, `.env.example`, Make targets, and local wrapper scripts.
- Keep all future implementation work on feature branches and merge back to `master` after verification.

## Phase 1 - Compose MVP Foundation

- Add Maven monorepo with shared event model and Spring Boot service modules.
- Add Python FastAPI `ai-incident-assistant`.
- Add Dockerfiles and `docker-compose.yml` for gateway, services, Keycloak, PostgreSQL, Redis, Kafka-compatible broker, observability, and AI assistant.
- Add service-owned PostgreSQL schemas/databases and seed data.

## Phase 2 - Core Banking Flow

- Implement customer read model and seeded customers.
- Implement account ownership, balances, currencies, and account state checks.
- Implement payment create/query/cancel APIs, idempotency, state transitions, and transactional outbox.
- Implement audit event ingestion and immutable audit reads.
- Implement notification consumer retry/DLQ and demo failure switches.

## Phase 3 - Security and Gateway

- Configure Keycloak realm, clients, scopes, roles, and demo users.
- Configure gateway JWT validation, role/scope route authorization, Redis rate limits, security headers, standard errors, and correlation/trace propagation.
- Add tests for 401, 403, ownership isolation, 429, and audit creation.
- Map implemented controls to OWASP API risks.

## Phase 4 - Observability and AI

- Add metrics, tracing, JSON logs, dashboards, alerts, and runbooks.
- Implement AI incident assistant evidence extraction from logs, metrics snapshots, trace IDs, Kafka lag/DLQ samples, and runbooks.
- Add deterministic incident fixtures and tests proving no unsupported cause claims.

## Phase 5 - Quality Gates

- Add unit, integration, security, and E2E tests.
- Add CI with build, test, scan placeholders, image build, and Compose smoke test.
- Verify fresh clone workflow with `make up`, `make seed`, `make demo`, `make test`, and `make integration-test`.

## Phase 6 - Polish

- Add architecture and sequence diagrams, screenshots, Postman collection, production-readiness notes, limitations, roadmap, and CV/LinkedIn summary.
- Add Kubernetes/Helm only after the Compose MVP is green.

## Next Branch Increment

The next branch should run only when external verification becomes available:

- After an approved push, verify the GitHub Actions workflow on GitHub.
- Optionally run `K8S_SMOKE_APPLY=true make k8s-smoke` when pullable images and external infrastructure endpoints are available.
- Once remote CI passes, update `STATUS.md` and `docs/completion-audit.md` from partial to complete.
