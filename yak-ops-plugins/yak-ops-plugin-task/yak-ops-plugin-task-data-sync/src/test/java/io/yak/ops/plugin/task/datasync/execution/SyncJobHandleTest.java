package io.yak.ops.plugin.task.datasync.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Checks that exceptional Job results preserve authoritative terminal status. */
class SyncJobHandleTest {

    @Test
    void successfulJobProducesFinishedOutcome() {
        assertEquals(TaskOutcome.FINISHED, handle(JobStatus.FINISHED).completion().toCompletableFuture().join());
    }

    @Test
    void canceledJobDoesNotBecomeFailedBecauseResultFutureIsExceptional() {
        assertEquals(TaskOutcome.CANCELED, handle(JobStatus.CANCELED).completion().toCompletableFuture().join());
    }

    @Test
    void failedJobIsNeverReportedAsCanceled() {
        assertEquals(TaskOutcome.FAILED, handle(JobStatus.FAILED).completion().toCompletableFuture().join());
    }

    private static SyncJobHandle handle(JobStatus terminal) {
        JobID identity = JobID.generate();
        JobClient client = new JobClient() {
            @Override
            public JobID getJobID() {
                return identity;
            }

            @Override
            public CompletableFuture<JobStatus> getJobStatus() {
                return CompletableFuture.completedFuture(terminal);
            }

            @Override
            public CompletableFuture<Void> cancel() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
                if (terminal == JobStatus.FINISHED) {
                    return CompletableFuture.completedFuture(new JobExecutionResult(identity, 1));
                }
                return CompletableFuture.failedFuture(new IllegalStateException("Job has no successful result"));
            }
        };
        return new SyncJobHandle(client);
    }
}
