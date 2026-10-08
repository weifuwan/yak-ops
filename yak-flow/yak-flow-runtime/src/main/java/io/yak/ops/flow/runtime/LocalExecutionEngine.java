package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.sink.Sink;
import io.yak.ops.flow.api.source.Boundedness;
import io.yak.ops.flow.api.source.Source;
import io.yak.ops.flow.api.source.SourceSplit;
import java.time.Duration;
import java.util.Objects;

/**
 * YakFlow 单节点执行引擎入口，把一个 Source 与一个 Sink 连接为本地批流统一执行。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class LocalExecutionEngine {

    private static final int DEFAULT_CHANNEL_CAPACITY = 64;
    private static final int DEFAULT_SOURCE_PARALLELISM = 1;
    private static final int MAX_SOURCE_PARALLELISM = 16;
    private static final Duration DEFAULT_STREAM_CHECKPOINT_INTERVAL = Duration.ofSeconds(10);

    private final Duration streamCheckpointInterval;

    public LocalExecutionEngine() {
        this(DEFAULT_STREAM_CHECKPOINT_INTERVAL);
    }

    public LocalExecutionEngine(Duration streamCheckpointInterval) {
        this.streamCheckpointInterval =
                Objects.requireNonNull(streamCheckpointInterval, "streamCheckpointInterval must not be null");
        if (streamCheckpointInterval.isNegative()) {
            throw new IllegalArgumentException("streamCheckpointInterval must not be negative");
        }
    }

    /**
     * 以默认单 Source Reader 启动一次本地执行。
     *
     * @param source 输入 Source
     * @param sink 输出 Sink
     * @param schema Source 与 Sink 共享的逻辑表结构
     * @param <SplitT> Source 分片类型
     * @return 已启动的本地执行
     */
    public <SplitT extends SourceSplit> LocalExecution<SplitT> start(
            Source<SplitT> source, Sink sink, YakTableSchema schema) {
        return start(source, sink, schema, DEFAULT_SOURCE_PARALLELISM);
    }

    /**
     * 以指定 Source Reader 并行度启动一次本地执行。
     *
     * @param source 输入 Source
     * @param sink 输出 Sink
     * @param schema Source 与 Sink 共享的逻辑表结构
     * @param sourceParallelism bounded Source Reader 并行度
     * @param <SplitT> Source 分片类型
     * @return 已启动的本地执行
     */
    public <SplitT extends SourceSplit> LocalExecution<SplitT> start(
            Source<SplitT> source, Sink sink, YakTableSchema schema, int sourceParallelism) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (sourceParallelism <= 0 || sourceParallelism > MAX_SOURCE_PARALLELISM) {
            throw new IllegalArgumentException("sourceParallelism must be between 1 and " + MAX_SOURCE_PARALLELISM);
        }
        if (sourceParallelism > 1 && source.boundedness() != Boundedness.BOUNDED) {
            throw new IllegalArgumentException("parallel Source Readers currently require a bounded Source");
        }

        LocalExecution<SplitT> execution = new LocalExecution<>(
                source, sink, schema, DEFAULT_CHANNEL_CAPACITY, streamCheckpointInterval, sourceParallelism);
        execution.start();
        return execution;
    }
}
