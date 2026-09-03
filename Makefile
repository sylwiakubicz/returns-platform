.PHONY: help build test lint format run image verify-image

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
	./mvnw -B -ntp -pl returns-service spring-boot:run

image:        ## Build returns-service image into a tarball
	./mvnw -B -ntp -pl returns-service -am package jib:buildTar -DskipTests

verify-image: ## Print image architecture and layer count
	tar -xOf returns-service/target/jib-image.tar config.json | jq -r '.architecture, .os'
	tar -xOf returns-service/target/jib-image.tar manifest.json | jq '.[0].Layers | length'
