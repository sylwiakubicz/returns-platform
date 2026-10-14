.PHONY: help build test lint format run image verify-image namespace secrets infra-up infra-down infra-purge infra-status check-arch smoke migrate role-check
SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-16s\033[0m %s\n", $$1, $$2}'

build:        ## Compile and install all modules, skip tests
	./mvnw -B -ntp -T 1C clean install -DskipTests

test:         ## Run unit tests and quality gates
	./mvnw -B -ntp -T 1C verify

lint:         ## Check formatting
	./mvnw -B -ntp spotless:check

format:       ## Apply formatting
	./mvnw -B -ntp spotless:apply

run:          ## Run returns-service from the reactor
	./mvnw -B -ntp -pl returns-service -am install -DskipTests
	@test -f .env || { echo "missing .env"; exit 1; }; \
	set -a; source .env; set +a; \
	./mvnw -B -ntp -pl returns-service spring-boot:run -Dspring-boot.run.profiles=local

image:        ## Build returns-service image into a tarball
	./mvnw -B -ntp -pl returns-service -am package jib:buildTar -DskipTests

verify-image: ## Print image architecture and layer count
	tar -xOf returns-service/target/jib-image.tar config.json | jq -r '.architecture, .os'
	tar -xOf returns-service/target/jib-image.tar manifest.json | jq '.[0].Layers | length'

VM_HOST ?= rma.local
KAFKA_CLUSTER_ID ?= $(shell cat .kafka-cluster-id 2>/dev/null)
INFRA_WORKLOADS := deploy/registry deploy/redis deploy/minio deploy/schema-registry \
                   deploy/akhq deploy/keycloak deploy/wiremock

namespace:     ## Create the infra namespace
	kubectl apply -f k8s/bootstrap/

secrets: namespace   ## Create the infra-credentials secret from .env
	@test -f .env || { echo "missing .env, see task 5.4"; exit 1; }
	kubectl -n infra create secret generic infra-credentials \
		--from-env-file=.env --dry-run=client -o yaml | kubectl -n infra apply -f -

infra-up: namespace secrets   ## Apply infrastructure manifests and wait for readiness
	@test -n "$(KAFKA_CLUSTER_ID)" \
		|| { echo "missing .kafka-cluster-id, see task 5.7"; exit 1; }
	rm -f target/rendered-infra.yaml && mkdir -p target
	for f in k8s/infra/*.yaml; do \
		VM_HOST='$(VM_HOST)' KAFKA_CLUSTER_ID='$(KAFKA_CLUSTER_ID)' \
		envsubst '$$VM_HOST $$KAFKA_CLUSTER_ID' < $$f >> target/rendered-infra.yaml; \
		echo "---" >> target/rendered-infra.yaml; \
	done
	kubectl -n infra apply -f target/rendered-infra.yaml
	kubectl -n infra rollout status sts/postgres --timeout=300s
	kubectl -n infra rollout status sts/kafka --timeout=300s
	for w in $(INFRA_WORKLOADS); do \
		kubectl -n infra rollout status $$w --timeout=300s; \
	done
	kubectl -n infra wait --for=condition=complete job/minio-create-bucket --timeout=180s

infra-down:    ## Remove workloads, keep namespace and volumes
	kubectl -n infra delete deploy,sts,job,svc --all --ignore-not-found
	kubectl -n infra delete cm postgres-init akhq-config wiremock-mappings --ignore-not-found

infra-purge:   ## Remove everything including persistent volumes
	@echo "This deletes all data. Ctrl-C within 5 seconds to abort."; sleep 5
	kubectl delete namespace infra --ignore-not-found

infra-status:  ## Pod status and resource usage
	kubectl -n infra get pods -o wide
	-kubectl -n infra top pods

check-arch:    ## Verify that every image has an arm64 build
	./scripts/check-arch.sh

smoke:         ## Full infrastructure smoke test
	./scripts/smoke-infra.sh $(VM_HOST)

migrate: secrets ## Run returns-service database migrations on the VM via Flyway
	kubectl -n app delete job returns-flyway --ignore-not-found --wait=true
	kubectl -n app create configmap returns-migrations \
		--from-file=returns-service/src/main/resources/db/migration/ \
		--dry-run=client -o yaml | kubectl -n app apply -f -
	@kubectl -n infra get secret infra-credentials -o json \
		| jq -e '{apiVersion: "v1", kind: "Secret", \
		    metadata: {name: "returns-migrator", namespace: "app"}, type: "Opaque", \
		    data: {password: (.data.MIGRATOR_PASSWORD \
		      | if type == "string" and length > 0 then . else error("missing MIGRATOR_PASSWORD") end)}}' \
		| kubectl -n app apply -f -
	kubectl apply -f k8s/app/00-flyway-job.yaml
	@if kubectl -n app wait --for=condition=complete job/returns-flyway --timeout=610s; then \
		kubectl -n app logs job/returns-flyway; \
	else \
		kubectl -n app logs job/returns-flyway || true; \
		kubectl -n app describe job returns-flyway; \
		exit 1; \
	fi


role-check: ## Show returns_app privileges (expected: t, t, f, f, f)
	@test -f .env || { echo "missing .env"; exit 1; }; \
	set -a; source .env; set +a; \
	PGPASSWORD="$${RETURNS_APP_PASSWORD:?missing RETURNS_APP_PASSWORD}" psql \
		-v ON_ERROR_STOP=1 -h "$(VM_HOST)" -p 30432 -U returns_app \
		-d returns_platform \
		-c "SELECT \
		  has_table_privilege(current_user, 'returns.rma_events', 'SELECT') AS can_read, \
		  has_table_privilege(current_user, 'returns.rma_events', 'INSERT') AS can_append, \
		  has_table_privilege(current_user, 'returns.rma_events', 'UPDATE') AS can_update, \
		  has_table_privilege(current_user, 'returns.rma_events', 'DELETE') AS can_delete, \
		  has_schema_privilege(current_user, 'returns', 'CREATE') AS can_create;"

# curl http://localhost:8080/actuator/health
