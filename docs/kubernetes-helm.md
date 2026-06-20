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
