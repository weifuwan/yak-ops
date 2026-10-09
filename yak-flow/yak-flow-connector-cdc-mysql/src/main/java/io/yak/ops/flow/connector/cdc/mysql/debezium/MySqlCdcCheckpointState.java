package io.yak.ops.flow.connector.cdc.mysql.debezium;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MySQL CDC 最近已经交给 YakFlow Channel 的 Debezium source partition / offset 快照。
 *
 * @param sourcePartition Debezium source partition
 * @param sourceOffset Debezium source offset
 * @author weifuwan
 * @since 2026-09-27
 */
record MySqlCdcCheckpointState(Map<String, Object> sourcePartition, Map<String, Object> sourceOffset)
        implements CheckpointState {

    MySqlCdcCheckpointState {
        sourcePartition = Collections.unmodifiableMap(new LinkedHashMap<>(sourcePartition));
        sourceOffset = Collections.unmodifiableMap(new LinkedHashMap<>(sourceOffset));
    }

    static MySqlCdcCheckpointState empty() {
        return new MySqlCdcCheckpointState(Map.of(), Map.of());
    }
}
