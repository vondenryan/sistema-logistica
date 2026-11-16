#!/usr/bin/env bash
set -euo pipefail
workspace=$(cd "$(dirname "$0")/.." && pwd)
body_file=${1:-"$workspace/docs/PR.md"}
if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [path-to-PR-body]" >&2
  exit 2
fi
if [[ ! -f "$body_file" || ! -r "$body_file" ]]; then
  echo "PR body file not found or unreadable: $body_file" >&2
  echo 'Create and review docs/PR.md or pass an existing body file.' >&2
  exit 1
fi
cd "$workspace"
exec gh pr create --base develop --head feat/p1-sprint-1 --title 'feat: implement Pessoa 1 Sprint 1' --body-file "$body_file"
