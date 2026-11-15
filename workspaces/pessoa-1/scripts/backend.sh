#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then echo 'Run scripts/generate-env.sh first.' >&2; exit 1; fi
set -a
source .env
set +a
: "${DB_PASSWORD:?DB_PASSWORD is required}" "${JWT_SECRET:?JWT_SECRET is required}" "${DEMO_PASSWORD:?DEMO_PASSWORD is required}"
cd backend
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
