package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.plugin.task.api.execution.TaskHandle;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Engine-neutral view of one embedded YakFlow Job. */
final class SyncJobHandle implements TaskHandle {

    private final JobClient client;

    SyncJobHandle(JobClient client) {
        this.client = client;
    }

    @Override
    public Optional<TaskRuntimeReference> runtimeReference() {
        return Optional.of(new TaskRuntimeReference("YAKFLOW", client.getJobID().toString()));
    }

    @Override
    public CompletionStage<TaskOutcome> completion() {
        return client.getJobExecutionResult().handle((result, failure) -> {
            JobStatus status = client.getJobStatus().join();
            return switch (status) {
                case FINISHED -> TaskOutcome.FINISHED;
                case CANCELED -> TaskOutcome.CANCELED;
                case FAILED -> TaskOutcome.FAILED;
                default -> throw new IllegalStateException("YakFlow returned a result before terminal Job state");
            };
        });
    }

    @Override
    public CompletionStage<Void> cancel() {
        return client.cancel();
    }
}
