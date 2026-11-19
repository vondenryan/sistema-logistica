#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
# Exclusive creation prevents accidental replacement of a working environment.
if ! (set -o noclobber; : > .env) 2>/dev/null; then
  echo '.env already exists; keep it or remove it explicitly before regenerating.' >&2
  exit 1
fi
if ! python3 - <<'PY' > .env
import secrets
for name in ("DB_PASSWORD", "JWT_SECRET", "DEMO_PASSWORD"):
    print(f"{name}={secrets.token_hex(32)}")
PY
then
  rm -f .env
  exit 1
fi
echo 'Created .env with local random secrets (mode 600).'
