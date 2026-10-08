package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import java.util.Objects;

/**
 * 把 Source 检查点状态放入 Channel 顺序中的 barrier；Sink 消费到该消息时必须先 flush 前序数据。
 *
 * @param checkpointId 检查点标识
 * @param enumeratorState Enumerator 状态
 * @param readerState 当前 Reader 状态；无活动 Reader 时为 null
 * @param splitId 当前活动分片标识；无活动 Reader 时为 null
 * @author weifuwan
 * @since 2026-09-27
 */
record CheckpointBarrierMessage(
        long checkpointId, CheckpointState enumeratorState, CheckpointState readerState, String splitId)
        implements ChannelMessage {

    CheckpointBarrierMessage {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be greater than 0");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState must not be null");
    }
}
