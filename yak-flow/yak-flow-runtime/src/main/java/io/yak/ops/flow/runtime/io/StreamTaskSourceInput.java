package io.yak.ops.flow.runtime.io;

import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.flow.runtime.operators.SourceOperator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 将 SourceOperator 适配成 StreamTask 的输入。
 *
 * <p>不持有 Reader 的生命周期，不运行线程；只负责输出转发和可用性查询。
 * Channel 与 Checkpoint Barrier 将在通用 IO 契约稳定后接入。
 */
public final class StreamTaskSourceInput<T> {

    private final SourceOperator<T, ? extends SourceSplit> operator;
    private final ReaderOutput<T> output;

    public StreamTaskSourceInput(SourceOperator<T, ? extends SourceSplit> operator, ReaderOutput<T> output) {
        this.operator = Objects.requireNonNull(operator, "operator 不能为空");
        this.output = Objects.requireNonNull(output, "output 不能为空");
    }

    public InputStatus emitNext() throws Exception {
        return operator.emitNext(output);
    }

    public CompletableFuture<Void> getAvailableFuture() {
        return operator.isAvailable();
    }
}
