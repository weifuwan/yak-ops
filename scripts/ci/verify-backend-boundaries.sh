#!/usr/bin/env bash
# Architectural checks moved from the two redundant backend testing workflows.
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

if grep -R -n 'io\.yak\.ops\.flow\.runtime' yak-ops-core/src/main/java; then
    echo 'Core must not depend on YakFlow Runtime' >&2
    exit 1
fi

removed_paths=(
    yak-flow/yak-flow-connector-cdc-mysql
    yak-ops-business/yak-ops-business-data-sync/src/main/java/io/yak/ops/business/datasync/execution
    yak-ops-business/yak-ops-business-data-sync/src/test/java/io/yak/ops/business/datasync/execution
    yak-flow/yak-flow-api/src/main/java/io/yak/ops/flow/api/source
    yak-flow/yak-flow-api/src/main/java/io/yak/ops/flow/api/sink
    yak-flow/yak-flow-api/src/main/java/io/yak/ops/flow/api/checkpoint
    yak-flow/yak-flow-api/src/main/java/io/yak/ops/flow/api/trace
)
for path in "${removed_paths[@]}"; do
    if [[ -d "${path}" ]]; then
        echo "Removed legacy execution package is present: ${path}" >&2
        exit 1
    fi
done

if git grep -n -E \
    'io[.]yak[.]ops[.]flow[.]connector[.]|io[.]yak[.]ops[.]business[.]datasync[.]execution[.]|io[.]yak[.]ops[.]flow[.]api[.](source|sink|checkpoint|trace)[.]' \
    -- '*.java'; then
    echo 'Legacy data-sync engine import remained' >&2
    exit 1
fi

echo 'Core/Runtime ownership and legacy engine removal contracts passed.'
