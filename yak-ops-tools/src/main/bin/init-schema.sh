#!/bin/bash
set -euo pipefail

BIN_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_HOME="$(cd -- "${BIN_DIR}/.." && pwd)"
DATABASE="${DATABASE:-mysql}"

case "${DATABASE}" in
  mysql|postgresql) ;;
  *)
    echo "Unsupported DATABASE: ${DATABASE}. Use mysql or postgresql." >&2
    exit 2
    ;;
esac

if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
else
  JAVA_BIN="java"
fi

if ! command -v "${JAVA_BIN}" >/dev/null 2>&1; then
  echo "Java executable not found: ${JAVA_BIN}" >&2
  exit 1
fi

read -r -a JVM_OPTS <<< "${JAVA_OPTS:--server -Xms256m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError}"

exec "${JAVA_BIN}" "${JVM_OPTS[@]}" \
  -cp "${TOOLS_HOME}/conf:${TOOLS_HOME}/sql:${TOOLS_HOME}/libs/*" \
  -Dspring.profiles.active="init,${DATABASE}" \
  io.yak.ops.tools.datasource.InitializeYakOps "$@"
