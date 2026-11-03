#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

EXPECTED_VERSION="${1:-}"
bash scripts/release/check-release-metadata.sh "$EXPECTED_VERSION"

# shellcheck disable=SC1091
source release.env

VERSION="$YAK_OPS_VERSION"
FRONTEND_IMAGE="${DOCKERHUB_NAMESPACE}/yak-ops:${VERSION}"
BACKEND_IMAGE="${DOCKERHUB_NAMESPACE}/yak-ops-api:${VERSION}"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf 'Required command not found: %s\n' "$1" >&2
        exit 1
    fi
}

require_command docker
require_command curl
require_command python3

docker image inspect "$FRONTEND_IMAGE" >/dev/null
docker image inspect "$BACKEND_IMAGE" >/dev/null

export YAK_OPS_IMAGE="$FRONTEND_IMAGE"
export YAK_OPS_API_IMAGE="$BACKEND_IMAGE"
export YAK_OPS_PORT="${YAK_OPS_PORT:-19001}"
export MYSQL_HOST_PORT="${MYSQL_HOST_PORT:-13306}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-release_gate_mysql_root}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-yak_ops}"
export MYSQL_USER="${MYSQL_USER:-yak_ops}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-release_gate_mysql}"
export YAK_SECURITY_BOOTSTRAP_ENABLED=true
export YAK_SECURITY_BOOTSTRAP_USERNAME="${YAK_SECURITY_BOOTSTRAP_USERNAME:-release_root}"
export YAK_SECURITY_BOOTSTRAP_PASSWORD="${YAK_SECURITY_BOOTSTRAP_PASSWORD:-ReleaseGate@123456}"
export YAK_OPS_DATASOURCE_MASTER_KEY="${YAK_OPS_DATASOURCE_MASTER_KEY:-release_gate_only_master_key_change_me}"

# Use a dedicated Compose project and Docker resource names for each smoke run.
# Never allow cleanup to remove the regular deployment's named volumes.
SMOKE_PROJECT="yak-release-${BASHPID}-${RANDOM}"
SMOKE_OVERLAY="$(mktemp "${TMPDIR:-/tmp}/yak-release-overlay-XXXXXX.yaml")"
cat > "$SMOKE_OVERLAY" <<EOF
services:
  mysql:
    container_name: ${SMOKE_PROJECT}-mysql
  yak-ops-backend:
    container_name: ${SMOKE_PROJECT}-backend
  yak-ops-frontend:
    container_name: ${SMOKE_PROJECT}-frontend
volumes:
  mysql-data:
    name: ${SMOKE_PROJECT}-mysql-data
  yak-ops-data:
    name: ${SMOKE_PROJECT}-app-data
  yak-ops-logs:
    name: ${SMOKE_PROJECT}-logs
  yak-ops-jdbc-drivers:
    name: ${SMOKE_PROJECT}-drivers
networks:
  yak-ops-network:
    name: ${SMOKE_PROJECT}-network
EOF

compose() {
    docker compose --project-name "$SMOKE_PROJECT" -f compose.yaml -f "$SMOKE_OVERLAY" "$@"
}

COOKIE_FILE="$(mktemp)"
LOGIN_RESPONSE="$(mktemp)"
CURRENT_RESPONSE="$(mktemp)"

cleanup() {
    local status=$?
    trap - EXIT

    if (( status != 0 )); then
        printf '\nCompose smoke failed. Container status:\n' >&2
        compose ps >&2 || true
        printf '\nCompose logs:\n' >&2
        compose logs --no-color >&2 || true
    fi

    compose down -v --remove-orphans >/dev/null 2>&1 || true
    rm -f "$COOKIE_FILE" "$LOGIN_RESPONSE" "$CURRENT_RESPONSE" "$SMOKE_OVERLAY"
    exit "$status"
}
trap cleanup EXIT

printf 'Starting isolated Yak Ops Compose stack (%s)...\n' "$SMOKE_PROJECT"
compose config --quiet
compose up -d --no-build

wait_backend() {
    printf 'Waiting for backend health...\n'
    for _ in $(seq 1 90); do
        health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${SMOKE_PROJECT}-backend" 2>/dev/null || true)"
        if [[ "$health" == "healthy" ]]; then
            return 0
        fi
        sleep 2
    done
    printf 'Backend did not become healthy.\n' >&2
    return 1
}

mysql_query() {
    compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -D "$MYSQL_DATABASE" -N -B -e "$1"' sh "$1"
}

check_flyway_baseline() {
    local table_count history_count applied_count obsolete_count
    table_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'")"
    history_count="$(mysql_query "SELECT COUNT(*) FROM flyway_schema_history")"
    applied_count="$(mysql_query "SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1")"
    obsolete_count="$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('yak_ops_data_sync_task', 'yak_ops_data_sync_instance', 'yak_ops_task_attempt')")"
    if [[ "$table_count" != "25" || "$history_count" != "1" || "$applied_count" != "1" || "$obsolete_count" != "0" ]]; then
        printf 'Unexpected Flyway schema: tables=%s history=%s baseline=%s old_tables=%s\n' \
            "$table_count" "$history_count" "$applied_count" "$obsolete_count" >&2
        return 1
    fi
    printf 'Flyway baseline verified: 25 tables, V1 only, no obsolete tables.\n'
}

wait_backend
check_flyway_baseline
printf 'Restarting backend to verify Flyway does not rerun...\n'
compose restart yak-ops-backend
wait_backend
check_flyway_baseline

printf 'Waiting for frontend HTTP ingress...\n'
frontend_ready=false
for _ in $(seq 1 60); do
    if curl --fail --silent --show-error "http://127.0.0.1:${YAK_OPS_PORT}/" >/dev/null 2>&1; then
        frontend_ready=true
        break
    fi
    sleep 2
done

if [[ "$frontend_ready" != "true" ]]; then
    printf 'Frontend ingress did not become ready.\n' >&2
    exit 1
fi

printf 'Verifying login through frontend nginx ingress...\n'
curl --fail --silent --show-error \
    --cookie-jar "$COOKIE_FILE" \
    --header 'Content-Type: application/json' \
    --data "{\"userName\":\"${YAK_SECURITY_BOOTSTRAP_USERNAME}\",\"pw\":\"${YAK_SECURITY_BOOTSTRAP_PASSWORD}\"}" \
    "http://127.0.0.1:${YAK_OPS_PORT}/api/v1/account/login" \
    > "$LOGIN_RESPONSE"

python3 - "$LOGIN_RESPONSE" "$YAK_SECURITY_BOOTSTRAP_USERNAME" <<'PY'
import json
import sys

path, expected_user = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as stream:
    payload = json.load(stream)

if payload.get("code") != 200:
    raise SystemExit(f"login returned non-success payload: {payload}")

data = payload.get("data") or {}
if data.get("userName") != expected_user:
    raise SystemExit(f"login returned unexpected user: {payload}")
PY

printf 'Verifying authenticated current-user request...\n'
curl --fail --silent --show-error \
    --cookie "$COOKIE_FILE" \
    "http://127.0.0.1:${YAK_OPS_PORT}/api/v1/account/current" \
    > "$CURRENT_RESPONSE"

python3 - "$CURRENT_RESPONSE" "$YAK_SECURITY_BOOTSTRAP_USERNAME" <<'PY'
import json
import sys

path, expected_user = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as stream:
    payload = json.load(stream)

if payload.get("code") != 200:
    raise SystemExit(f"current-user returned non-success payload: {payload}")

data = payload.get("data") or {}
if data.get("userName") != expected_user:
    raise SystemExit(f"current-user returned unexpected user: {payload}")
PY

printf '\nCompose smoke passed for Yak Ops %s.\n' "$VERSION"
