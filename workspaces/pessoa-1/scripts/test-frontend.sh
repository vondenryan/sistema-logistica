#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../frontend"
export npm_config_cache="${npm_config_cache:-$PWD/../.cache/npm}"
npm ci
npm test -- --run
npm run build
