#!/usr/bin/env bash
set -uo pipefail

IMAGES=(
  "registry:2.8.3"
  "postgres:16.4-alpine"
  "redis:7.4-alpine"
  "minio/minio:RELEASE.2024-09-13T20-26-02Z"
  "minio/mc:RELEASE.2024-09-16T17-43-14Z"
  "apache/kafka:3.9.0"
  "confluentinc/cp-schema-registry:7.7.1"
  "tchiotludo/akhq:0.25.1"
  "quay.io/keycloak/keycloak:26.0"
  "wiremock/wiremock:3.9.1"
  "flyway/flyway:13.7.0"
)

failed=0
for img in "${IMAGES[@]}"; do
  archs=$(crane manifest "$img" 2>/dev/null \
    | jq -r '(.manifests // []) | map(.platform.architecture) | join(",")' || true)
  if [[ -z "$archs" ]]; then
    archs=$(crane config "$img" 2>/dev/null | jq -r '.architecture' || true)
  fi
  if [[ "$archs" == *arm64* ]]; then
    printf "OK      %-55s %s\n" "$img" "$archs"
  else
    printf "MISSING %-55s %s\n" "$img" "${archs:-unresolved}"
    failed=1
  fi
done
exit $failed
