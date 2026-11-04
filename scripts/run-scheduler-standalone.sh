#!/usr/bin/env bash
# Start the imported DolphinScheduler standalone in H2 mode on JDK 11.
# Java source and upstream execution model are not modified.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

version="$(java -version 2>&1 | sed -nE 's/.*version "([0-9]+)(\.[^"]*)?".*/\1/p' | head -n 1)"
if [[ "$version" != "11" ]]; then
  echo "The imported DolphinScheduler runtime requires JDK 11 for now; detected Java major version: ${version:-unknown}" >&2
  exit 1
fi

if [[ "${1:-}" == "--skip-build" ]]; then
  shift
else
  bash mvnw -B -ntp -f pom.xml \
    -pl :yak-ops-bom,:yak-ops-standalone-server -am \
    -Dmaven.test.skip=true -Dspotless.skip=true -Djacoco.skip=true \
    -Danalyze.skip=true -Dbuild.assembly.skip=true install
fi

# Upstream API/DAO jars intentionally exclude some configuration and SQL.
# The distribution supplies these as external conf files. For Maven exec:java,
# copy them only into target/classes, never into the upstream source tree.
runtime_classes="yak-ops-standalone-server/target/classes"
if [[ ! -d "$runtime_classes" ]]; then
  echo "Standalone target/classes does not exist; run the build before --skip-build." >&2
  exit 1
fi
mkdir -p "$runtime_classes/sql"
cp yak-ops-api/src/main/resources/task-type-config.yaml "$runtime_classes/"
cp yak-ops-api/src/main/resources/dynamic-task-type-config.yaml "$runtime_classes/"
cp yak-ops-dao/src/main/resources/sql/dolphinscheduler_h2.sql "$runtime_classes/sql/"

exec bash mvnw -B -ntp \
  -f yak-ops-standalone-server/pom.xml \
  -Dexec.mainClass=org.apache.dolphinscheduler.StandaloneServer \
  -Dexec.classpathScope=compile \
  org.codehaus.mojo:exec-maven-plugin:3.1.0:java
