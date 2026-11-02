package io.yak.ops.cdc.runtime.standalone;

import io.yak.ops.cdc.runtime.api.metrics.MetricGroup;
import io.yak.ops.cdc.runtime.api.sink.WriterInitContext;
import java.util.OptionalLong;

/** Single-JVM counterpart to Flink Sink V2's writer initialization context. */
final class LocalWriterContext implements WriterInitContext {

    private final String jobId;
    private final MetricGroup metrics;

    LocalWriterContext(String jobId, MetricGroup metrics) {
        this.jobId = jobId;
        this.metrics = metrics;
    }

    @Override
    public int getSubtaskId() {
        return 0;
    }

    @Override
    public int getNumberOfParallelSubtasks() {
        return 1;
    }

    @Override
    public int getAttemptNumber() {
        return 0;
    }

    @Override
    public OptionalLong getRestoredCheckpointId() {
        return OptionalLong.empty();
    }

    @Override
    public String getJobId() {
        return jobId;
    }

    @Override
    public ClassLoader getUserCodeClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public MetricGroup metricGroup() {
        return metrics;
    }

    @Override
    public boolean isObjectReuseEnabled() {
        return false;
    }
}
