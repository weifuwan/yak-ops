package io.yak.ops.flow.runtime.execution;

import io.yak.ops.core.api.dag.Pipeline;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.execution.PipelineExecutor;
import io.yak.ops.flow.runtime.executiongraph.ExecutionGraph;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamingJobGraphGenerator;
import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Embedded Pipeline submission endpoint. Compiles a physical JobGraph, creates an ExecutionGraph
 * and returns a lightweight client; it does not own execution threads or attempt state.
 */
public final class EmbeddedPipelineExecutor implements PipelineExecutor {

    @Override
    public CompletableFuture<JobClient> execute(Pipeline pipeline, Configuration configuration) {
        try {
            Objects.requireNonNull(pipeline, "pipeline");
            Objects.requireNonNull(configuration, "configuration");
            if (!(pipeline instanceof StreamGraph streamGraph)) {
                throw new IllegalArgumentException("EmbeddedPipelineExecutor 只支持 StreamGraph");
            }

            JobGraph jobGraph = new StreamingJobGraphGenerator(streamGraph, configuration).generate();
            ExecutionGraph executionGraph = new ExecutionGraph(jobGraph);
            executionGraph.start();
            return CompletableFuture.completedFuture(new EmbeddedJobClient(executionGraph));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
