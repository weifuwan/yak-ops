package io.yak.ops.plugin.task.datasync.pipeline;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.plugin.task.datasync.execution.TableExecutionProgress;
import java.util.Objects;

/** One physical graph, its configuration, and optional bounded-table observations. */
public record CompiledPipeline(StreamGraph graph, Configuration configuration, TableExecutionProgress progress) {
    public CompiledPipeline {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(configuration, "configuration");
    }

    public CompiledPipeline(StreamGraph graph, Configuration configuration) {
        this(graph, configuration, null);
    }
}
