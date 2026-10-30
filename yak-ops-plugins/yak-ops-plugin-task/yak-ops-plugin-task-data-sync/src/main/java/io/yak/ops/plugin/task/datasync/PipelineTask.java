package io.yak.ops.plugin.task.datasync;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;

/** Thin DolphinScheduler-style Task lifecycle adapter for a Core PipelineExecution. */
public final class PipelineTask extends AbstractTask implements TaskHandle {
    private final PipelineExecution job;

    public PipelineTask(PipelineExecution job) {
        this.job = java.util.Objects.requireNonNull(job, "job");
    }

    @Override
    public CompletionStage<TaskOutcome> handle() {
        start();
        return completion();
    }

    @Override
    public void start() {
        job.start();
    }

    @Override
    public CompletionStage<TaskOutcome> completion() {
        return job.completion().thenApply(PipelineTask::toOutcome);
    }

    @Override
    public CompletionStage<Void> cancel() {
        return job.cancel();
    }

    @Override
    public Optional<TaskRuntimeReference> runtimeReference() {
        return job.engineJobId().map(id -> new TaskRuntimeReference("YAKFLOW", id));
    }

    @Override
    public CompletionStage<Optional<TaskRuntimeReference>> runtimeReferenceReady() {
        return job.engineJobIdReady().thenApply(id -> id.map(value -> new TaskRuntimeReference("YAKFLOW", value)));
    }

    @Override
    public OptionalInt progressPercent() {
        return job.getProgressPercent();
    }

    @Override
    public Optional<String> executionReport() {
        return job.getExecutionReport();
    }

    @Override
    public Optional<String> executionSummary() {
        return job.getExecutionSummary();
    }

    private static TaskOutcome toOutcome(JobStatus status) {
        return switch (status) {
            case FINISHED -> TaskOutcome.FINISHED;
            case FAILED -> TaskOutcome.FAILED;
            case CANCELED -> TaskOutcome.CANCELED;
            default -> throw new IllegalStateException("Data Sync completed without a terminal Job status");
        };
    }
}
