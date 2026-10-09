#!/usr/bin/env bash
# Detect which quality checks are relevant without adding a separate Actions job.
# Unknown paths and non-PR/non-push events run every check (fail closed).
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

backend=false
frontend=false
distribution=false
changed=()

mark_all() {
    backend=true
    frontend=true
    distribution=true
}

if [[ "${1:-}" == "--paths" ]]; then
    shift
    changed=("$@")
else
    case "${CI_EVENT_NAME:-${GITHUB_EVENT_NAME:-}}" in
        pull_request)
            : "${CI_PR_BASE_SHA:?Missing pull request base SHA}"
            : "${CI_PR_HEAD_SHA:?Missing pull request head SHA}"
            changed_file="$(mktemp)"
            trap 'rm -f "${changed_file}"' EXIT
            git diff --name-only -z "${CI_PR_BASE_SHA}...${CI_PR_HEAD_SHA}" > "${changed_file}"
            mapfile -d '' -t changed < "${changed_file}"
            ;;
        push)
            : "${CI_CURRENT_SHA:?Missing pushed commit SHA}"
            if [[ -z "${CI_PUSH_BEFORE_SHA:-}" || "${CI_PUSH_BEFORE_SHA}" =~ ^0+$ ]]; then
                mark_all
            else
                changed_file="$(mktemp)"
                trap 'rm -f "${changed_file}"' EXIT
                git diff --name-only -z "${CI_PUSH_BEFORE_SHA}" "${CI_CURRENT_SHA}" > "${changed_file}"
                mapfile -d '' -t changed < "${changed_file}"
            fi
            ;;
        *)
            # Includes workflow_call from the formal Release Gate: always perform full quality.
            mark_all
            ;;
    esac
fi

for file in "${changed[@]}"; do
    case "${file}" in
        .github/workflows/*|scripts/ci/*)
            mark_all
            ;;
        yak-ops-ui/*)
            frontend=true
            case "${file}" in
                yak-ops-ui/pom.xml|yak-ops-ui/package.json|yak-ops-ui/package-lock.json)
                    distribution=true
                    ;;
            esac
            ;;
        yak-ops-dist/*|Dockerfile|docker/*|compose.yaml|compose.without-mysql.yaml|.env.example|README.md|NOTICE)
            distribution=true
            ;;
        release.env|scripts/release/*)
            backend=true
            distribution=true
            ;;
        pom.xml)
            backend=true
            distribution=true
            ;;
        yak-flow/*|yak-ops-bom/*|yak-ops-core/*|yak-ops-common/*|yak-ops-platform/*|yak-ops-dao/*|yak-ops-spi/*|yak-ops-business/*|yak-ops-plugins/*|yak-ops-boot/*|.mvn/*|mvnw|mvnw.cmd|scripts/verify_logging_rules.py)
            backend=true
            ;;
        docs/*|*.md|.gitignore|.asf.yaml|LICENSE)
            # Documentation-only PRs do not need Maven/npm, but the required check still runs.
            ;;
        *)
            # An unclassified build/configuration change must never skip a potentially affected test.
            mark_all
            ;;
    esac
done

{
    printf 'backend=%s\n' "${backend}"
    printf 'frontend=%s\n' "${frontend}"
    printf 'distribution=%s\n' "${distribution}"
} | if [[ -n "${GITHUB_OUTPUT:-}" ]]; then tee -a "${GITHUB_OUTPUT}"; else cat; fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf 'Change scope: backend=%s, frontend=%s, distribution=%s\n' \
        "${backend}" "${frontend}" "${distribution}" >> "${GITHUB_STEP_SUMMARY}"
fi
