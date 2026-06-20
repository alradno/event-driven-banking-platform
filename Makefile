SHELL := /bin/sh
COMPOSE ?= podman compose
MVNW ?= ./mvnw
PYTHON ?= python3
HELM ?= helm

.PHONY: up down seed scan test ci-local integration-test e2e-test demo logs smoke \
	simulate-payment-stuck simulate-notification-failure simulate-auth-failure \
	simulate-high-latency simulate-kafka-lag helm-lint helm-template \
	k8s-validate k8s-smoke

up:
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down -v

seed:
	./scripts/seed.sh

scan:
	./scripts/ci-scan.sh

test:
	$(MVNW) test
	cd ai-incident-assistant && $(PYTHON) -m unittest discover -s tests

ci-local:
	COMPOSE="$(COMPOSE)" MVNW="$(MVNW)" PYTHON="$(PYTHON)" ./scripts/ci-local.sh

integration-test:
	$(MVNW) verify -Pintegration
	./scripts/smoke-test.sh
	./scripts/e2e-test.sh

e2e-test:
	./scripts/e2e-test.sh

demo:
	./scripts/demo.sh

logs:
	$(COMPOSE) logs -f --tail=200

smoke:
	./scripts/smoke-test.sh

simulate-payment-stuck:
	./scripts/simulations/payment-stuck.sh

simulate-notification-failure:
	./scripts/simulations/notification-failure.sh

simulate-auth-failure:
	./scripts/simulations/auth-failure.sh

simulate-high-latency:
	./scripts/simulations/high-latency.sh

simulate-kafka-lag:
	./scripts/simulations/kafka-lag.sh

helm-lint:
	$(HELM) lint --strict deploy/helm/banking-platform

helm-template:
	$(HELM) template banking deploy/helm/banking-platform

k8s-validate:
	HELM="$(HELM)" ./scripts/k8s-validate.sh

k8s-smoke:
	HELM="$(HELM)" ./scripts/k8s-smoke.sh
