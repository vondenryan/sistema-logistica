#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then echo 'Local .env is required; configure local credentials first.' >&2; exit 1; fi
set -a
source .env
set +a
: "${DB_PASSWORD:?DB_PASSWORD is required in .env or environment}" "${JWT_SECRET:?JWT_SECRET is required in .env or environment}" "${DEMO_PASSWORD:?DEMO_PASSWORD is required in .env or environment}"
: "${FREIGHT_TARIFF_PER_KM:?Set FREIGHT_TARIFF_PER_KM explicitly for this demo; no deployment tariff has been approved}"
export ROUTING_PROVIDER=fake FREIGHT_TARIFF_PER_KM
cd backend
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
