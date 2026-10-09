#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

expect_scope() {
    local expected="$1"
    shift
    local actual
    actual="$(env -u GITHUB_OUTPUT -u GITHUB_STEP_SUMMARY bash scripts/ci/detect-changes.sh --paths "$@")"
    if [[ "${actual}" != "${expected}" ]]; then
        printf 'Scope mismatch for paths: %s\nExpected:\n%s\nActual:\n%s\n' \
            "$*" "${expected}" "${actual}" >&2
        exit 1
    fi
}

all_false="$(printf 'backend=false\nfrontend=false\ndistribution=false')"
backend_only="$(printf 'backend=true\nfrontend=false\ndistribution=false')"
frontend_only="$(printf 'backend=false\nfrontend=true\ndistribution=false')"
distribution_only="$(printf 'backend=false\nfrontend=false\ndistribution=true')"
backend_and_distribution="$(printf 'backend=true\nfrontend=false\ndistribution=true')"
frontend_and_distribution="$(printf 'backend=false\nfrontend=true\ndistribution=true')"
all_true="$(printf 'backend=true\nfrontend=true\ndistribution=true')"

expect_scope "${all_false}" docs/capabilities/yak-flow/README.md
expect_scope "${backend_only}" yak-flow/yak-flow-runtime/src/main/java/StreamTask.java
expect_scope "${backend_only}" yak-ops-business/yak-ops-business-data-sync/src/main/java/Foo.java
expect_scope "${frontend_only}" yak-ops-ui/apps/web/src/App.tsx
expect_scope "${frontend_and_distribution}" yak-ops-ui/package-lock.json
expect_scope "${distribution_only}" Dockerfile
expect_scope "${backend_and_distribution}" pom.xml
expect_scope "${all_true}" .github/workflows/quality-check.yml
expect_scope "${all_true}" scripts/ci/detect-changes.sh
expect_scope "${all_true}" .editorconfig
expect_scope "${all_true}" yak-flow/yak-flow-runtime/src/main/java/StreamTask.java yak-ops-ui/apps/web/src/App.tsx Dockerfile

# A reusable Release Gate invocation must never selectively skip quality.
actual="$(env -u GITHUB_OUTPUT -u GITHUB_STEP_SUMMARY CI_EVENT_NAME=workflow_dispatch \
    bash scripts/ci/detect-changes.sh)"
[[ "${actual}" == "${all_true}" ]] || { echo 'Release quality cannot be conditional' >&2; exit 1; }

echo 'CI change-scope regression checks passed.'
