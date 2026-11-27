#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export DB_PASSWORD="$(openssl rand -hex 24)"
export JWT_SECRET="$(openssl rand -hex 32)"
export P2_SMOKE_PASSWORD="$(openssl rand -hex 16)"
export INTEGRATION_PROFILE=integration-demo
export OSRM_BASE_URL=http://osrm:19090
export FREIGHT_TARIFF_PER_KM=2.50
export GATEWAY_PORT="${TEST_GATEWAY_PORT:-18000}"
export MAILPIT_UI_PORT="${TEST_MAILPIT_PORT:-18025}"
export P2_SMOKE_URL="http://127.0.0.1:$GATEWAY_PORT"
export P3_SMOKE_MAILPIT_URL="http://127.0.0.1:$MAILPIT_UI_PORT"
export NO_PROXY="127.0.0.1,localhost"
export no_proxy="$NO_PROXY"
scratch="$(mktemp -d)"
export INTEGRATION_FIXTURE_FILE="$scratch/fixture.json"
compose=(docker compose --env-file /dev/null -p "logistica-check-$(openssl rand -hex 5)" -f compose.yml -f compose.test.yml)
cleanup() {
  result=$?
  if (( result != 0 )); then "${compose[@]}" logs --tail=80; fi
  "${compose[@]}" down -v --rmi local
  rm -rf "$scratch"
}
trap cleanup EXIT

db() { "${compose[@]}" exec -T -e P2_SMOKE_PASSWORD -e INTEGRATION_DRIVER_ID postgres "$@"; }
sql() { db psql -X -U logistics -d logistics -v ON_ERROR_STOP=1 "$@"; }

"${compose[@]}" up -d --build --wait
sql < ../workspaces/pessoa-2/scripts/seed-integration-actors.sql
sql < ../workspaces/pessoa-3/scripts/seed-smoke-drivers.sql
python3 smoke.py register
export INTEGRATION_DRIVER_ID="$(python3 -c 'import json,os; print(json.load(open(os.environ["INTEGRATION_FIXTURE_FILE"]))["driver"])')"
sql < seed-journey-driver.sql
python3 smoke.py
if [[ "${RUN_BROWSER_SMOKE:-0}" == 1 ]]; then python3 browser_smoke.py; fi

history() {
  sql -Atc 'SELECT version, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
            SELECT version, checksum, success FROM pessoa2.flyway_schema_history ORDER BY installed_rank;
            SELECT version, checksum, success FROM deliveries.flyway_schema_history ORDER BY installed_rank;
            SELECT id, status FROM routing.routes ORDER BY id;
            SELECT assignment_id, released_at FROM pessoa2.fleet_assignments ORDER BY assignment_id;'
}
history > "$scratch/before"
db pg_dump -U logistics -Fc logistics > "$scratch/history.dump"
"${compose[@]}" stop backend
db psql -U logistics -d postgres -v ON_ERROR_STOP=1 -c 'ALTER DATABASE logistics RENAME TO original'
db createdb -U logistics logistics
db pg_restore -U logistics --exit-on-error -d logistics < "$scratch/history.dump"
"${compose[@]}" up -d --wait
history > "$scratch/after"
diff -u "$scratch/before" "$scratch/after"
curl --fail --silent "$P2_SMOKE_URL/ready" >/dev/null
echo 'Banco restaurado iniciou com checksums Flyway e registros preservados.'
