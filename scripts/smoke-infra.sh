#!/usr/bin/env bash
set -euo pipefail
HOST="${1:?usage: smoke-infra.sh <host>}"
set -a && source .env && set +a

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
