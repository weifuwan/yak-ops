package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.checkpoint.CheckpointState;
import java.util.Objects;
import java.util.Optional;

/**
 * Local Execution Engine 完成的一次检查点切面，表示对应 Source 状态之前的数据已经被 Sink flush。
 *
 * @param checkpointId 当前执行内单调递增的检查点标识
 * @param enumeratorState Source Enumerator 状态
 * @param readerState 当前 Reader 状态；检查点发生在分片之间时为 null
 * @param splitId 当前 Reader 对应的分片标识；检查点发生在分片之间时为 null
 * @param completedAtMillis Sink 完成 barrier flush 的时间戳
 * @author weifuwan
 * @since 2026-09-27
 */
public record LocalCheckpoint(
        long checkpointId,
        CheckpointState enumeratorState,
        CheckpointState readerState,
        String splitId,
        long completedAtMillis) {

    public LocalCheckpoint {
        if (checkpointId <= 0) {
            throw new IllegalArgumentException("checkpointId must be greater than 0");
        }
        Objects.requireNonNull(enumeratorState, "enumeratorState must not be null");
    }

    /**
     * 以 Optional 形式读取可能不存在的 Reader 状态。
     *
     * @return Reader 状态
     */
    public Optional<CheckpointState> readerStateOptional() {
        return Optional.ofNullable(readerState);
    }

    /**
     * 以 Optional 形式读取可能不存在的活动分片标识。
     *
     * @return 活动分片标识
     */
    public Optional<String> splitIdOptional() {
        return Optional.ofNullable(splitId);
    }
}
