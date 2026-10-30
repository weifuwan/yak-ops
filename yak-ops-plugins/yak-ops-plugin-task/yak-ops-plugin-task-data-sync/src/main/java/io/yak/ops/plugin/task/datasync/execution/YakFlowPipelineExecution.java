package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.client.PipelineExecution;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;

/** Adapts the verified Task execution handle without introducing a second physical runtime. */
public final class YakFlowPipelineExecution implements PipelineExecution {
    private final String jobId;
    private final TaskHandle handle;
    private volatile JobStatus status = JobStatus.CREATED;

    public YakFlowPipelineExecution(String jobId, TaskHandle handle) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.handle = Objects.requireNonNull(handle, "handle");
        handle.completion().whenComplete((outcome, failure) -> {
            if (failure != null) status = JobStatus.FAILING;
            else if (outcome != null) status = status(outcome);
        });
    }

    @Override
    public String getJobId() {
        return jobId;
    }

    @Override
    public synchronized void start() {
        if (status != JobStatus.CREATED) return;
        status = JobStatus.RUNNING;
        handle.start();
    }

    @Override
    public JobStatus getStatus() {
        return status;
    }

    @Override
    public CompletionStage<JobStatus> completion() {
        return handle.completion().thenApply(YakFlowPipelineExecution::status);
    }

    @Override
    public CompletionStage<Void> cancel() {
        if (!status.isTerminalState()) status = JobStatus.CANCELLING;
        return handle.cancel();
    }

    @Override
    public OptionalInt getProgressPercent() {
        return handle.progressPercent();
    }

    @Override
    public Optional<String> getExecutionReport() {
        return handle.executionReport();
    }

    @Override
    public Optional<String> getExecutionSummary() {
        return handle.executionSummary();
    }

    @Override
    public CompletionStage<Optional<String>> engineJobIdReady() {
        return handle.runtimeReferenceReady().thenApply(reference -> reference.map(TaskRuntimeReference::id));
    }

    @Override
    public Optional<String> engineJobId() {
        return handle.runtimeReference().map(TaskRuntimeReference::id);
    }

    private static JobStatus status(TaskOutcome outcome) {
        return switch (outcome) {
            case FINISHED -> JobStatus.FINISHED;
            case FAILED -> JobStatus.FAILED;
            case CANCELED -> JobStatus.CANCELED;
        };
    }
}
