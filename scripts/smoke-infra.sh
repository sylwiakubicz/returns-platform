#!/usr/bin/env bash
set -euo pipefail
HOST="${1:?usage: smoke-infra.sh <host>}"
set -a && source .env && set +a

echo "== Registry"
curl -sf "http://${HOST}:30500/v2/_catalog" | jq -e '.repositories' > /dev/null

echo "== Postgres: service role can use its own schema"
PGPASSWORD="$RETURNS_APP_PASSWORD" psql -h "$HOST" -p 30432 -U returns_app \
  -d returns_platform -tAc "SELECT current_schema();" | grep -qx returns

echo "== Postgres: cross-schema access must be denied for the right reason"
out=$(PGPASSWORD="$RETURNS_APP_PASSWORD" psql -v ON_ERROR_STOP=1 -h "$HOST" -p 30432 \
        -U returns_app -d returns_platform \
        -c "CREATE TABLE logistics.hack(id int);" 2>&1) \
  && { echo "FAIL: schema isolation is not enforced"; exit 1; }
grep -q "permission denied for schema logistics" <<<"$out" \
  || { echo "FAIL: denied for a different reason: $out"; exit 1; }

echo "== Postgres: pgcrypto reachable through the ext schema"
PGPASSWORD="$RETURNS_APP_PASSWORD" psql -h "$HOST" -p 30432 -U returns_app \
  -d returns_platform -tAc "SELECT gen_random_uuid();" > /dev/null

echo "== Redis"
kubectl -n infra exec deploy/redis -- redis-cli ping | grep -qx PONG

echo "== MinIO bucket created by the job"
kubectl -n infra get job minio-create-bucket -o jsonpath='{.status.succeeded}' | grep -qx 1

echo "== Kafka: topic lifecycle"
kubectl -n infra exec kafka-0 -- env KAFKA_HEAP_OPTS="-Xms16m -Xmx96m" \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic smoke-test --partitions 3
kubectl -n infra exec kafka-0 -- env KAFKA_HEAP_OPTS="-Xms16m -Xmx96m" \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic smoke-test

echo "== Kafka: external listener reachable from this machine"
kcat -b "${HOST}:30092" -L -m 10 > /dev/null

echo "== Schema registry"
curl -sf "http://${HOST}:30081/subjects" | jq -e 'type == "array"' > /dev/null

echo "== AKHQ"
curl -sf "http://${HOST}:30085/health" > /dev/null

echo "== Keycloak"
curl -sf "http://${HOST}:30809/health/ready" | jq -e '.status == "UP"' > /dev/null

echo "== WireMock"
curl -sf "http://${HOST}:30888/__admin/mappings" | jq -e '.meta.total >= 0' > /dev/null

echo
echo "All checks passed."
