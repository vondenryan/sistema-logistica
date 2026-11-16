#!/usr/bin/env bash
set -euo pipefail
umask 077
root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d "${TMPDIR:-/tmp}/p1-sprint3.XXXXXXXX")
container=''
backend_pid=''
cleanup() {
  if [[ -n "$backend_pid" ]]; then kill "$backend_pid" 2>/dev/null || :; wait "$backend_pid" 2>/dev/null || :; fi
  if [[ -n "$container" ]]; then docker rm -fv "$container" >/dev/null 2>&1 || :; fi
  rm -rf -- "$tmp"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$tmp/home" "$tmp/build" "$tmp/cache"
# Copy build inputs and cached tools: Maven writes only inside this private directory.
cp -a "$root/backend/pom.xml" "$root/backend/src" "$tmp/build/"
cp -a --reflink=auto "$root/backend/.cache/apache-maven-3.9.11" "$root/backend/.cache/repository" "$tmp/cache/"
chmod -R go-rwx "$tmp"
[[ -x "$tmp/cache/apache-maven-3.9.11/bin/mvn" ]] || { echo 'Cached Maven missing' >&2; exit 1; }
cat > "$tmp/settings.xml" <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>
EOF
(cd "$tmp/build" && MAVEN_OPTS="-Duser.home=$tmp/home -Djava.io.tmpdir=$tmp" "$tmp/cache/apache-maven-3.9.11/bin/mvn" -o -s "$tmp/settings.xml" -Dmaven.repo.local="$tmp/cache/repository" -DskipTests package > "$tmp/build.log" 2>&1) || { echo 'Offline Maven package failed (cached dependencies required)' >&2; exit 1; }
cp "$tmp/build/target/backend-1.0.0.jar" "$tmp/backend.jar"
export POSTGRES_PASSWORD DB_PASSWORD JWT_SECRET DEMO_PASSWORD
POSTGRES_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
DB_PASSWORD=$POSTGRES_PASSWORD
JWT_SECRET=$(python3 -c 'import secrets; print(secrets.token_hex(32))')
DEMO_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_hex(24))')
container=$(docker create --pull never -e POSTGRES_PASSWORD -e POSTGRES_USER=logistics -e POSTGRES_DB=logistics -p 127.0.0.1::5432 postgres:16)
docker start "$container" >/dev/null
ready=0
for ((i=0; i<60; i++)); do
  if docker exec "$container" pg_isready -q -U logistics -d logistics; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'PostgreSQL readiness timed out' >&2; exit 1; }
pg_port=$(docker port "$container" 5432/tcp)
pg_port=${pg_port##*:}
[[ "$pg_port" =~ ^[0-9]+$ ]] || { echo 'Could not determine PostgreSQL port' >&2; exit 1; }
# Select an available port; a competing process can still claim it before Java binds.
app_port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
export DB_URL="jdbc:postgresql://127.0.0.1:$pg_port/logistics" DB_USER=logistics
export ROUTING_PROVIDER=fake FREIGHT_TARIFF_PER_KM=2.50 SPRING_PROFILES_ACTIVE=dev SERVER_PORT="$app_port" SERVER_ADDRESS=127.0.0.1
export SMOKE_BASE_URL="http://127.0.0.1:$app_port/api/v1"
# Give Java only the required local environment, without putting secrets in arguments.
python3 - "$tmp/backend.jar" > "$tmp/backend.log" 2>&1 <<'PYJAVA' &
import os, sys
names = ('PATH', 'HOME', 'DB_URL', 'DB_USER', 'DB_PASSWORD', 'JWT_SECRET', 'DEMO_PASSWORD', 'ROUTING_PROVIDER', 'FREIGHT_TARIFF_PER_KM', 'SPRING_PROFILES_ACTIVE', 'SERVER_PORT', 'SERVER_ADDRESS')
os.execvpe('java', ['java', '-Duser.home=' + os.path.dirname(sys.argv[1]) + '/home', '-Djava.io.tmpdir=' + os.path.dirname(sys.argv[1]), '-jar', sys.argv[1]], {name: os.environ[name] for name in names if name in os.environ})
PYJAVA
backend_pid=$!
ready=0
for ((i=0; i<120; i++)); do
  kill -0 "$backend_pid" 2>/dev/null || { echo 'Backend exited during startup' >&2; exit 1; }
  if python3 -c 'import json,os,urllib.request; r=urllib.request.Request(os.environ["SMOKE_BASE_URL"]+"/auth/login", data=json.dumps({"email":"admin@example.invalid","password":os.environ["DEMO_PASSWORD"]}).encode(),headers={"Content-Type":"application/json"}); urllib.request.urlopen(r,timeout=1).read()' >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'Backend login readiness timed out' >&2; exit 1; }
python3 - "$root/scripts/smoke-sprint3.py" <<'PYSMOKE'
import os, sys
names = ('PATH', 'HOME', 'DEMO_PASSWORD', 'SMOKE_BASE_URL')
env = {name: os.environ[name] for name in names if name in os.environ}
env['PYTHONDONTWRITEBYTECODE'] = '1'
os.execvpe('python3', ['python3', sys.argv[1]], env)
PYSMOKE
# The database is fresh and owned exclusively by this run.
counts=$(docker exec "$container" psql -U logistics -d logistics -At -v ON_ERROR_STOP=1 -c "SELECT (SELECT count(*) FROM orders.orders WHERE status='CONFIRMED'), (SELECT count(*) FROM billing.freight_quotes), (SELECT count(*) FROM routing.routes WHERE status='IN_PROGRESS'), (SELECT count(*) FROM routing.route_stops);")
[[ "$counts" == '2|2|1|2' ]] || { echo 'Database invariants failed' >&2; exit 1; }
echo 'PASS: disposable PostgreSQL persistence counts (2 orders, 2 quotes, 1 route, 2 stops)'
