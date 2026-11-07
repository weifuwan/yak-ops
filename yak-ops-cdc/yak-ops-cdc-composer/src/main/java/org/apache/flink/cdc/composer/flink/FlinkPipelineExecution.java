package org.apache.flink.cdc.composer.flink;

import org.apache.flink.cdc.composer.PipelineExecution;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.function.Consumer;

/**
 * A pipeline execution that run the defined pipeline via Flink's {@link
 * StreamExecutionEnvironment}.
 */
public class FlinkPipelineExecution implements PipelineExecution {

    private final StreamExecutionEnvironment env;
    private final String jobName;
    private final boolean isBlocking;

    public FlinkPipelineExecution(
            StreamExecutionEnvironment env, String jobName, boolean isBlocking) {
        this.env = env;
        this.jobName = jobName;
        this.isBlocking = isBlocking;
    }

    @Override
    public ExecutionInfo execute() throws Exception {
        return executeManaged(jobClient -> {});
    }

    /**
     * Give the embedding task a cancellation handle as soon as the job is submitted.
     * Waiting for the result preserves the task executor's RUNNING lifecycle.
     */
    public ExecutionInfo executeManaged(Consumer<JobClient> onSubmitted) throws Exception {
        JobClient jobClient = env.executeAsync(jobName);
        onSubmitted.accept(jobClient);
        if (isBlocking) {
            jobClient.getJobExecutionResult().get();
        }
        return new ExecutionInfo(jobClient.getJobID().toString(), jobName);
    }
}
