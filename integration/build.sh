#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
p1="$root/workspaces/pessoa-1"
p2="$root/workspaces/pessoa-2"
p3="$root/workspaces/pessoa-3"
out="$root/integration/artifacts"

"${MAVEN:-mvn}" --batch-mode -f "$p2/backend/pom.xml" -Pintegration clean package -DskipTests
for frontend in "$p1/frontend" "$p2/frontend" "$p3/frontend"; do
  npm --prefix "$frontend" ci
done
npm --prefix "$p1/frontend" run build -- --mode integration --base /pedidos/
P2_FRONTEND_BASE=/frota/ npm --prefix "$p2/frontend" run build -- --mode integration
npm --prefix "$p3/frontend" run build:integration -- --base /entregas/

mkdir -p "$out/web"
install -m 644 "$p2/backend/target/pessoa2-backend-0.0.1-SNAPSHOT-integration.jar" "$out/app.jar"
install -m 644 "$root/integration/nginx.conf" "$out/nginx.conf"
install -m 644 "$root/integration/index.html" "$out/web/index.html"
for app in pedidos frota entregas; do
  rm -rf "$out/web/$app"
done
cp -R "$p1/frontend/dist" "$out/web/pedidos"
cp -R "$p2/frontend/dist" "$out/web/frota"
cp -R "$p3/frontend/dist-integration" "$out/web/entregas"
