#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

BIN_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TOOLS_HOME="$(cd -- "${BIN_DIR}/.." && pwd)"
DATABASE="${DATABASE:-postgresql}"

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
  io.yak.ops.tools.datasource.InitializeDolphinScheduler "$@"
