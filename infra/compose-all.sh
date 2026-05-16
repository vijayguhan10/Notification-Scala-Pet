#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

run_compose() {
  local service_dir="$1"
  shift
  ( cd "$repo_root/infra/$service_dir" && docker compose "$@" )
}

usage() {
  cat <<'EOF'
Usage:
  compose-all.sh up
  compose-all.sh down

Notes:
  - Runs docker compose in: infra/kafka, infra/rabbitmq, infra/redis
  - Bind-mount data stays on disk under infra/*/data when using ./data mounts
EOF
}

cmd="${1:-}"
shift || true

case "$cmd" in
  up)
    # Start infra services
    run_compose kafka up -d "$@"
    run_compose rabbitmq up -d "$@"
    run_compose redis up -d "$@"
    ;;

  down)
    # Stop infra services (reverse order is a bit nicer)
    run_compose redis down "$@" || true
    run_compose rabbitmq down "$@" || true
    run_compose kafka down "$@" || true
    ;;

  *)
    usage
    exit 2
    ;;
esac
