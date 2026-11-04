#!/usr/bin/env bash
# Compile the flattened Scheduler reactor without bringing in CDC (JDK 17+).
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
if [[ "$#" -eq 0 ]]; then
  set -- compile
fi
projects="$(find yak-ops-* -type f -name pom.xml \
  ! -path 'yak-ops-cdc/*' ! -path 'yak-ops-ui/*' \
  | sort | sed 's#/pom.xml$##' | paste -sd, -)"
if [[ -z "$projects" ]]; then
  echo "No Scheduler POM files found" >&2
  exit 1
fi
exec bash mvnw -B -ntp -pl "$projects" -am \
  -Dmaven.test.skip=true -Dspotless.skip=true -Djacoco.skip=true \
  -Danalyze.skip=true "$@"
