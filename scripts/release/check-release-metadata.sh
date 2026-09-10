#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f release.env ]]; then
    printf 'release.env is missing\n' >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
source release.env
set +a

failed=0

check() {
    local description="$1"
    shift
    if "$@"; then
        printf 'OK  %s\n' "$description"
    else
        printf 'ERR %s\n' "$description" >&2
        failed=1
    fi
}

check "Yak Ops version uses SemVer" \
    bash -c '[[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]' _ "$YAK_OPS_VERSION"
check "Maven framework dependency matches release metadata" \
    grep -Fq "<yak-framework.version>${YAK_FRAMEWORK_VERSION}</yak-framework.version>" yak-ops-bom/pom.xml
check "Frontend package version matches release metadata" \
    grep -Fq "\"version\": \"${YAK_OPS_VERSION}\"" yak-ops-ui/package.json
check "Frontend Docker example tag matches release metadata" \
    grep -Fq "YAK_OPS_IMAGE=${DOCKERHUB_NAMESPACE}/yak-ops:${YAK_OPS_VERSION}" .env.example
check "Backend Docker example tag matches release metadata" \
    grep -Fq "YAK_OPS_API_IMAGE=${DOCKERHUB_NAMESPACE}/yak-ops-api:${YAK_OPS_VERSION}" .env.example

if (( failed != 0 )); then
    printf '\nRelease metadata is inconsistent. Update release.env and the checked files together.\n' >&2
    exit 1
fi

printf '\nRelease metadata is consistent for Yak Ops %s.\n' "$YAK_OPS_VERSION"
