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
- Notification DLQ replay support added on branch `feat/notification-dlq-replay`.
- OpenTelemetry trace export E2E coverage added on branch `test/trace-export-e2e`.
- Observability dashboard, alert loading, and screenshot evidence added on branch `feat/observability-dashboards-alerts`.
- Kubernetes/Helm service chart added on branch `feat/kubernetes-helm`.
- Negative payment and replay-safety E2E coverage added on branch `test/payment-negative-e2e`.
- Testcontainers/WireMock and event schema compatibility coverage added on branch `test/testcontainers-wiremock-schema`.
- Kubernetes smoke readiness script and Make target added on branch `test/kubernetes-cluster-smoke-readiness`.
- Completion audit added on branch `docs/final-objective-audit`.
- Repository scan gate added on branch `ci/repository-scan-gate`.
- Kubernetes API smoke passed on temporary `kind`/Podman cluster on branch `docs/kind-k8s-smoke-evidence`.
- Local CI parity target added and passed on branch `ci/local-ci-parity`.
- GitHub Actions and Jenkins now reuse the local CI parity flow on branch `ci/reuse-local-parity-flow`.

## In Progress

- Waiting for remote CI verification after a push from GitHub credentials with repository write access.

## Not Yet Complete

- Remote GitHub Actions CI has not been verified because the local commits have not been pushed by an account with repository write access.

## Local Tooling Observed

- Java available locally: OpenJDK 25, compiling target Java 21.
- Maven not installed locally; repository will include `./mvnw`.
- Python available locally: Python 3.13.
- Docker CLI not installed locally; Podman is available and exposes a Docker-compatible socket that Testcontainers can use.
- Kubernetes CLI available locally: `kubectl` is installed. A temporary `kind` cluster was created with Podman for API server smoke validation and can be deleted after evidence capture.

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
- Completed: E2E runtime contract branch merged back to `master`.
- Passed: `./mvnw -pl notification-service -am test`; notification replay parser unit coverage passed.
- Passed: `make integration-test`; Maven `verify -Pintegration`, smoke, and E2E runtime contract completed with notification DLQ replay evidence.
- Passed: `make e2e-test`; additionally forced notification failure, observed `bank.payment.events.notification-service.dlq` growth, replayed one matching correlation ID, and observed `bank.notification.events` growth after replay.
- Passed: `make simulate-notification-failure`; real failure produced a DLQ record, replayed one message, and the AI report cited DLQ evidence with the trace ID.
- Passed: all five simulation targets: payment stuck, notification failure, auth failure, high latency, and Kafka lag.
- Completed: notification DLQ replay branch merged back to `master`.
- Passed: `make e2e-test`; additionally verified OpenTelemetry collector output contains the exported `banking.trace_id` tag for the Compose payment flow.
- Passed: `make integration-test`; Maven `verify -Pintegration`, smoke, and E2E runtime contract completed with OpenTelemetry trace-export evidence.
- Passed: all five simulation targets again after trace-export changes: payment stuck, notification failure, auth failure, high latency, and Kafka lag.
- Completed: OpenTelemetry trace export E2E branch merged back to `master`.
- Passed: `promtool check config /etc/prometheus/prometheus.yml`; Prometheus config loaded one rule file with eight alert rules.
- Passed: Prometheus targets API reported `up=1` for gateway, customer, account, payment, audit, and notification services after permitting local demo metrics scraping.
- Passed: Grafana API found provisioned dashboard `banking-platform-overview` with the expanded banking observability panels.
- Captured: Grafana dashboard screenshot at `docs/screenshots/grafana-banking-overview.png` after running the Compose E2E flow.
- Passed: `make test`.
- Passed: `make integration-test`; Maven `verify -Pintegration`, smoke, and E2E runtime contract completed after dashboard/alert changes.
- Completed: observability dashboard and alerts branch merged back to `master`.
- Passed: `helm lint deploy/helm/banking-platform` using `alpine/helm:3.15.4`.
- Passed: `helm template banking deploy/helm/banking-platform` using `alpine/helm:3.15.4`.
- Passed: `make helm-template` with `HELM` pointed at the containerized Helm runner.
- Passed: `make test` after adding the Helm chart and Kubernetes documentation.
- Completed: Kubernetes/Helm chart branch merged back to `master`.
- Passed: `sh -n scripts/e2e-test.sh`.
- Passed: `make e2e-test`; additionally verified idempotency key reuse with a changed body returns `409`, insufficient-funds/frozen-source/ownership-mismatch payments are rejected and audited, and no-match notification DLQ replay returns zero without publishing a notification event.
- Passed: `make integration-test`; Maven `verify -Pintegration`, smoke, and the hardened E2E runtime contract completed with negative payment and replay-safety assertions.
- Passed: `make test` after adding the negative E2E coverage.
- Passed: `podman compose config`.
- Passed: `git diff --check`.
- Passed: secret scan for the provided Bitbucket and Sonar token patterns returned no matches.
- Completed: Negative payment E2E branch merged back to `master`.
- Passed: `./mvnw -pl banking-common test`; event envelope v1 compatibility fixtures deserialize, unknown payload fields are preserved, and unsupported/missing envelope versions are rejected.
- Passed: `./mvnw test`; unit suites passed and the new Testcontainers/WireMock IT classes compiled.
- Passed: `./mvnw verify -Pintegration`; Testcontainers ran PostgreSQL-backed payment-service ITs with WireMock and Kafka-backed notification-service ITs against a real broker.
- Passed: `make up` after rebuilding the changed Java service images; Postgres, Kafka, Redis, Keycloak, Java services, AI assistant, Prometheus, Grafana, and OTel collector started locally.
- Passed: `make integration-test`; Maven `verify -Pintegration`, smoke, and the Compose E2E runtime contract completed with the new Testcontainers/WireMock/schema coverage in place.
- Passed: `make test`, `podman compose config`, and `git diff --check` after the Testcontainers/WireMock/schema branch changes.
- Passed: secret scan for the provided Bitbucket and Sonar token patterns returned no matches after the Testcontainers/WireMock/schema branch changes.
- Completed: Testcontainers/WireMock/schema compatibility branch merged back to `master`.
- Passed: `sh -n scripts/k8s-validate.sh scripts/k8s-smoke.sh`.
- Passed: `make helm-lint` using `alpine/helm:3.15.4`; strict Helm linting succeeded with only the informational missing-icon recommendation.
- Passed: `make helm-template` using `alpine/helm:3.15.4`; rendered 766 lines of Kubernetes manifests.
- Passed: `make k8s-validate` using `alpine/helm:3.15.4`; offline manifest checks verified seven Deployments, seven Services, probes, scrape annotations, config, and default Postgres Secret wiring.
- Superseded: `make k8s-smoke` was previously blocked by missing `kubectl current-context`, then later passed against a temporary `kind` cluster.
- Passed: `make test`, `podman compose config`, and `git diff --check` after the Kubernetes smoke readiness branch changes.
- Passed: secret scan for the provided Bitbucket and Sonar token patterns returned no matches after the Kubernetes smoke readiness branch changes.
- Completed: Kubernetes smoke readiness branch merged back to `master`.
- Completed: objective audit in `docs/completion-audit.md`; remaining incomplete check is remote CI verification after push.
- Completed: final objective audit branch merged back to `master`.
- Passed: `make scan`; repository hygiene and secret-pattern checks found no forbidden tracked artifacts or token/private-key patterns.
- Passed: `make test`, `podman compose config`, and `git diff --check` after adding the CI repository scan gate.
- Completed: repository scan gate branch merged back to `master`.
- Fixed: CI scan gate no longer matches its own pattern definitions; `make scan` passes after the self-exclusion fix.
- Passed: fresh local clone from `/Users/aradlowskinova/Desktop/event-driven-banking-platform` into `/tmp/event-driven-banking-fresh-clone.tanvj0`; `make scan`, `make test`, `podman compose config`, and `make k8s-validate` with containerized Helm all passed without paid services or API keys.
- Passed: temporary `kind` v0.32.0 cluster on Podman with Kubernetes node `v1.36.1`; `make k8s-smoke` using `alpine/helm:3.15.4` passed Helm lint and Kubernetes server-side dry-run against context `kind-banking-smoke`.
- Completed: Kubernetes smoke evidence branch merged back to `master`.
- Passed: `make ci-local`; repository scans, Java unit tests, AI assistant unit tests, image builds, Compose readiness checks, smoke test, and E2E runtime contract completed locally before any push.
- Completed: local CI parity branch merged back to `master`.
- Updated: GitHub Actions and Jenkins delegate to `make ci-local`, so remote CI uses the same readiness-aware scan/test/build/smoke/E2E flow that passed locally.
- Passed: `make ci-local` again after switching GitHub Actions and Jenkins to the shared parity flow.
