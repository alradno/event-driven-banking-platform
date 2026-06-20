# Kubernetes and Helm

The Compose stack remains the canonical local demo because it includes PostgreSQL, Kafka, Redis, Keycloak, Prometheus, Grafana, and the OpenTelemetry collector. The Helm chart is the Kubernetes packaging layer for the banking platform services after those backing services exist in the target cluster.

## Chart

Chart path:

```sh
deploy/helm/banking-platform
```

The chart deploys:

- `api-gateway`
- `customer-service`
- `account-service`
- `payment-service`
- `audit-service`
- `notification-service`
- `ai-incident-assistant`

It also creates shared configuration, a PostgreSQL credential secret when no existing secret is supplied, ClusterIP services, health probes, Prometheus scrape annotations, and optional ingress for the gateway.

## Render

```sh
helm template banking deploy/helm/banking-platform
```

If Helm is not installed locally, the same render can be checked with a container:

```sh
podman run --rm \
  -v "$PWD:/work" \
  -w /work \
  alpine/helm:3.15.4 \
  template banking deploy/helm/banking-platform
```

## Validate

`make k8s-validate` does not require a Kubernetes cluster. It runs strict Helm linting, renders the chart, parses the manifest, and verifies the default chart contract: seven Deployments, seven Services, one ServiceAccount, one ConfigMap, default PostgreSQL Secret wiring, readiness/liveness probes, Prometheus scrape annotations, and gateway service URLs.

```sh
make k8s-validate
```

If Helm is not installed locally, provide the same containerized Helm runner used for render checks:

```sh
HELM="podman run --rm -v $PWD:/work -w /work alpine/helm:3.15.4" make k8s-validate
```

## Smoke

The Kubernetes smoke script requires `kubectl` to have a reachable current context. By default it runs a server-side dry-run against the selected cluster, which validates the rendered objects with the Kubernetes API without installing them:

```sh
make k8s-smoke
```

If Helm is not installed locally, provide the containerized Helm runner:

```sh
HELM="podman run --rm -v $PWD:/work -w /work alpine/helm:3.15.4" make k8s-smoke
```

For an actual install/rollout smoke, make sure the external dependencies below exist in the target cluster, then run:

```sh
K8S_SMOKE_APPLY=true make k8s-smoke
```

The install smoke uses release `banking` and namespace `banking` by default, waits for all enabled deployments, rejects common pod failure states such as `ImagePullBackOff` and `CrashLoopBackOff`, port-forwards the gateway and AI assistant services, and checks `/actuator/health` plus `/health`. Override defaults with `K8S_RELEASE`, `K8S_NAMESPACE`, `K8S_FULLNAME`, `K8S_GATEWAY_LOCAL_PORT`, and `K8S_AI_LOCAL_PORT`.

Use [values-smoke.example.yaml](../deploy/helm/banking-platform/values-smoke.example.yaml) as a starting point for image repositories and dependency endpoints:

```sh
HELM_EXTRA_ARGS="-f deploy/helm/banking-platform/values-smoke.example.yaml" \
  K8S_SMOKE_APPLY=true \
  make k8s-smoke
```

## Cluster Prerequisites

The real cluster smoke cannot pass with only this chart. The chart packages the application services and expects these dependencies to be reachable:

| Dependency | Required for | Notes |
| --- | --- | --- |
| Pullable service images | All Deployments | Default image repositories are local Compose/Podman names; override them for a cluster registry. |
| PostgreSQL | Customer, account, payment, and audit services | Create `customer_db`, `account_db`, `payment_db`, and `audit_db`; prefer `postgres.existingSecret`. |
| Kafka | Payment outbox, audit, and notification workflows | Enable topic auto-create or pre-create the banking topics. |
| Redis | Gateway rate limiting | Configure `external.redisHost` and `external.redisPort`. |
| Keycloak | Gateway JWT validation and authenticated smoke flows | The issuer/JWK URLs are in-cluster URLs; host-side token acquisition may need a separate reachable Keycloak URL. |
| OpenTelemetry collector | Trace export evidence | Required only when validating trace export. |
| Prometheus/Grafana | Observability dashboards | Not chart-owned; the chart only adds scrape annotations. |

If `kubectl config current-context` is not set, `make k8s-smoke` fails fast and reports that blocker instead of pretending the cluster smoke ran.

## Install

```sh
helm upgrade --install banking deploy/helm/banking-platform \
  --namespace banking \
  --create-namespace \
  --set external.kafkaBootstrapServers=kafka.banking.svc.cluster.local:9092 \
  --set external.redisHost=redis.banking.svc.cluster.local \
  --set external.keycloakIssuerUri=http://keycloak.banking.svc.cluster.local/realms/banking \
  --set external.keycloakJwkSetUri=http://keycloak.banking.svc.cluster.local/realms/banking/protocol/openid-connect/certs \
  --set postgres.host=postgres.banking.svc.cluster.local
```

For a real environment, prefer `postgres.existingSecret` over inline values:

```sh
helm upgrade --install banking deploy/helm/banking-platform \
  --namespace banking \
  --create-namespace \
  --set postgres.existingSecret=banking-postgres-credentials
```

## Access

Without ingress:

```sh
kubectl -n banking port-forward svc/banking-platform-api-gateway 18080:8080
```

With ingress, set `ingress.enabled=true` and provide the gateway host/TLS values for the cluster.

## Notes

- The image repositories default to the local image names produced by Compose/Podman builds. Override `services.*.image.repository` and `services.*.image.tag` for a registry.
- The chart deliberately does not template stateful infrastructure. PostgreSQL, Kafka, Redis, Keycloak, Prometheus, Grafana, and OpenTelemetry should come from managed services or dedicated infrastructure charts.
- The gateway metrics endpoint is scrapeable for local demo observability. Production deployments should isolate actuator metrics on an internal management port or a private network policy.
