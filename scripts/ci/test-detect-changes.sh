#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

expect_scope() {
    local expected="$1"
    shift
    local actual
    actual="$(env -u GITHUB_OUTPUT -u GITHUB_STEP_SUMMARY bash scripts/ci/detect-changes.sh --paths "$@" | grep -E '^(backend|frontend|distribution)=')"
    if [[ "${actual}" != "${expected}" ]]; then
        printf 'Scope mismatch for paths: %s\nExpected:\n%s\nActual:\n%s\n' "$*" "${expected}" "${actual}" >&2
        exit 1
    fi
}

all_false="$(printf 'backend=false\nfrontend=false\ndistribution=false')"
backend_only="$(printf 'backend=true\nfrontend=false\ndistribution=false')"
frontend_only="$(printf 'backend=false\nfrontend=true\ndistribution=false')"
distribution_only="$(printf 'backend=false\nfrontend=false\ndistribution=true')"
frontend_and_distribution="$(printf 'backend=false\nfrontend=true\ndistribution=true')"
all_true="$(printf 'backend=true\nfrontend=true\ndistribution=true')"

expect_scope "${all_false}" DEVELOPMENT.md
expect_scope "${all_false}" README.md
expect_scope "${backend_only}" yak-ops-cdc/yak-ops-cdc-runtime/src/main/java/StreamTask.java
expect_scope "${backend_only}" yak-ops-business/yak-ops-business-task/src/main/java/DefinitionService.java
expect_scope "${backend_only}" yak-ops-task-execution/src/main/java/io/yak/ops/task/execution/TaskExecutionService.java
expect_scope "${frontend_only}" yak-ops-ui/apps/web/app/layout/ProductSidebar.tsx
expect_scope "${all_false}" yak-ops-ui/FRONTEND_RULES.md
expect_scope "${frontend_only}" yak-ops-ui/scripts/check-architecture.mjs
expect_scope "${frontend_and_distribution}" yak-ops-ui/package-lock.json
expect_scope "${distribution_only}" Dockerfile
expect_scope "${all_true}" pom.xml
expect_scope "${all_true}" .github/workflows/quality-check.yml
expect_scope "${all_true}" .editorconfig
expect_scope "${all_true}" yak-ops-cdc/yak-ops-cdc-runtime/src/main/java/StreamTask.java yak-ops-ui/apps/web/app/layout/ProductSidebar.tsx Dockerfile

# Reusable Release Gate runs never skip quality, tests, or packaging.
actual="$(env -u GITHUB_OUTPUT -u GITHUB_STEP_SUMMARY CI_EVENT_NAME=workflow_dispatch bash scripts/ci/detect-changes.sh)"
[[ "${actual}" == *'backend=true'* && "${actual}" == *'frontend=true'* && "${actual}" == *'distribution=true'* && "${actual}" == *'release=true'* && "${actual}" == *'tests=true'* ]] || { echo 'Release quality cannot be conditional' >&2; exit 1; }
# Enforce backend naming without an extra workflow or database test matrix.
python3 -m unittest discover -s scripts/ci -p 'test_backend_naming.py' -v
python3 -m unittest discover -s scripts/ci -p 'test_yoips.py' -v
python3 scripts/ci/check_backend_naming.py
echo 'CI change-scope regression checks passed.'
