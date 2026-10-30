package io.yak.ops.sync.runtime.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.sync.runtime.execution.TableExecutionProgress;
import java.util.Objects;

/** One physical graph, its configuration, and optional bounded-table observations. */
public final class CompiledPipeline {
    private final StreamGraph graph;
    private final Configuration configuration;
    private final TableExecutionProgress progress;

    @JsonCreator
    public CompiledPipeline(
            @JsonProperty("graph") StreamGraph graph,
            @JsonProperty("configuration") Configuration configuration,
            @JsonProperty("progress") TableExecutionProgress progress) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(configuration, "configuration");
        this.graph = graph;
        this.configuration = configuration;
        this.progress = progress;
    }

    @JsonProperty("graph")
    public StreamGraph graph() {
        return graph;
    }

    @JsonProperty("configuration")
    public Configuration configuration() {
        return configuration;
    }

    @JsonProperty("progress")
    public TableExecutionProgress progress() {
        return progress;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof CompiledPipeline other)) return false;
        return java.util.Objects.equals(graph, other.graph)
                && java.util.Objects.equals(configuration, other.configuration)
                && java.util.Objects.equals(progress, other.progress);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(graph, configuration, progress);
    }
}
