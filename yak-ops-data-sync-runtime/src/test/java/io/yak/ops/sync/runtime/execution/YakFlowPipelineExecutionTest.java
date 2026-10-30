package io.yak.ops.sync.runtime.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.execution.JobClient;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Checks that exceptional Job results preserve authoritative terminal status. */
class YakFlowPipelineExecutionTest {

    @Test
    void successfulJobProducesFinishedOutcome() {
        assertEquals(JobStatus.FINISHED, handle(JobStatus.FINISHED).toCompletableFuture().join());
    }

    @Test
    void canceledJobDoesNotBecomeFailedBecauseResultFutureIsExceptional() {
        assertEquals(JobStatus.CANCELED, handle(JobStatus.CANCELED).toCompletableFuture().join());
    }

    @Test
    void failedJobIsNeverReportedAsCanceled() {
        assertEquals(JobStatus.FAILED, handle(JobStatus.FAILED).toCompletableFuture().join());
    }

    @Test
    void completionWaitsAsynchronouslyForTerminalJobStatus() {
        JobID identity = JobID.generate();
        CompletableFuture<JobStatus> status = new CompletableFuture<>();
        JobClient client = new JobClient() {
            @Override
            public JobID getJobID() {
                return identity;
            }

            @Override
            public CompletableFuture<JobStatus> getJobStatus() {
                return status;
            }

            @Override
            public CompletableFuture<Void> cancel() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
                return CompletableFuture.completedFuture(new JobExecutionResult(identity, 1));
            }
        };
        CompletableFuture<JobStatus> completion = YakFlowPipelineExecution.terminalStatus(client).toCompletableFuture();
        assertFalse(completion.isDone());
        status.complete(JobStatus.FINISHED);
        assertEquals(JobStatus.FINISHED, completion.join());
    }

    private static java.util.concurrent.CompletionStage<JobStatus> handle(JobStatus terminal) {
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
        return YakFlowPipelineExecution.terminalStatus(client);
    }
}
