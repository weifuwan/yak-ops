package io.yak.ops.cdc.runtime.api.sink;

import io.yak.ops.cdc.runtime.api.metrics.MetricGroup;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

/** Local counterpart of Flink 1.20.3 WriterInitContext, without cluster-only services. */
public interface WriterInitContext {
    int getSubtaskId();

    int getNumberOfParallelSubtasks();

    int getAttemptNumber();

    OptionalLong getRestoredCheckpointId();

    String getJobId();

    ClassLoader getUserCodeClassLoader();

    MetricGroup metricGroup();

    boolean isObjectReuseEnabled();

    default <T> Optional<Consumer<T>> metadataConsumer() {
        return Optional.empty();
    }
}
