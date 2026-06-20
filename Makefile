SHELL := /bin/sh
COMPOSE ?= podman compose
MVNW ?= ./mvnw
PYTHON ?= python3
HELM ?= helm

.PHONY: up down seed test integration-test e2e-test demo logs smoke \
	simulate-payment-stuck simulate-notification-failure simulate-auth-failure \
	simulate-high-latency simulate-kafka-lag helm-template

up:
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down -v

seed:
	./scripts/seed.sh

test:
	$(MVNW) test
	cd ai-incident-assistant && $(PYTHON) -m unittest discover -s tests

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

helm-template:
	$(HELM) template banking deploy/helm/banking-platform
