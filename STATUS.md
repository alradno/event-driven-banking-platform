# Project Status

Last updated: 2026-06-20

## Overall Status

Status: In progress.

The repository started from the initial `README.md` only. The first implementation branch is `feat/portfolio-monorepo-foundation`, created from `master`.

## Completed

- Objective reviewed from Codex attachment.
- Local repo inspected.
- Implementation branch created before coding.
- Specification, implementation plan, status file, and initial ADRs created.
- Maven monorepo added with common library, gateway, customer, account, payment, audit, and notification services.
- Payment idempotency hashing, payment state machine, and transactional outbox implemented.
- Rule-based FastAPI AI incident assistant implemented with evidence/no-evidence tests.
- Docker Compose, Makefile, scripts, OpenAPI, Postman collection, runbooks, CI, and Jenkinsfile added.
- Compose runtime hardened on branch `fix/compose-runtime-startup` for local Podman: Apache Kafka broker, high published ports, Keycloak import fixes, and gateway OIDC/JWK configuration.
- Compose E2E runtime contract added on branch `test/e2e-runtime-contract`.

## In Progress

- Completion audit against the full portfolio objective.

## Not Yet Complete

- More negative and failure-mode E2E coverage, especially retry/DLQ replay and trace export inspection.
- Complete dashboards, alerts, and screenshots.
- Kubernetes/Helm.

## Local Tooling Observed

- Java available locally: OpenJDK 25, compiling target Java 21.
- Maven not installed locally; repository will include `./mvnw`.
- Python available locally: Python 3.13.
- Docker not installed locally; Podman is available.

## Completion Evidence Log

- Passed: `python3 -m unittest discover -s ai-incident-assistant/tests`.
- Passed: `./mvnw test -q`.
- Passed: `make test`.
- Passed: `podman compose config`.
- Passed: `podman compose build ai-incident-assistant`.
- Passed: `podman compose build customer-service`.
- Failed then addressed: `make up` could not pull `docker.redpanda.com/redpandadata/redpanda:v24.2.10` because Podman rejected the registry certificate, local ports `6379`/`3000` were occupied, and Java images for Kafka/Keycloak hit `SIGILL` on local aarch64 Podman. Runtime fix branch switched the broker to Docker Hub `apache/kafka`, parameterized high published ports, and disables JVM SVE for affected containers.
- Passed: `make up` against local Podman with Postgres, Kafka, Redis, Keycloak, all Java services, AI assistant, Prometheus, Grafana, and OTel collector running on non-conflicting local ports.
- Passed: gateway health at `http://localhost:18080/actuator/health`.
- Passed: AI assistant health at `http://localhost:18090/health`.
- Passed: Keycloak password-grant token checks for `alice`, `support`, and `sre` after adding complete demo user profile fields.
- Passed: `make smoke`; duplicate idempotency key returned the same payment ID and the AI incident endpoint returned evidence/likely causes.
- Passed: `make integration-test`; Maven `verify -Pintegration`, smoke, and E2E runtime contract completed.
- Passed: gateway log scan after smoke found no recurrence of the previous `RequestRateLimiterGatewayFilterFactory`/`ReadOnlyHttpHeaders` exception.
- Completed: runtime fix branch merged back to `master`.
- Passed: `make e2e-test`; verified 401, 403, 429, customer/account ownership isolation, payment completion and idempotency, outbox publication, Kafka payment offset growth, notification event publication, audit records by correlation/trace ID, gateway Prometheus metrics, and evidence-backed AI output.
- Pending: merge E2E runtime contract branch back to `master`.
