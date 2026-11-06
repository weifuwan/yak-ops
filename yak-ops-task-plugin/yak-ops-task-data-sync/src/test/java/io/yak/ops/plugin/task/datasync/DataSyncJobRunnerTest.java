package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.plugin.task.api.TaskExecutionContext;

import org.apache.flink.core.execution.JobClient;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

class DataSyncJobRunnerTest {

    @Test
    void cancellationClosesSubmittedJob() throws Exception {
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext());
        JobClient job = mock(JobClient.class);
        when(job.cancel()).thenReturn(CompletableFuture.completedFuture(null));

        runner.onSubmitted(job);
        runner.cancel();

        assertTrue(runner.isCancelRequested());
        verify(job).cancel();
    }

    @Test
    void lateSubmissionAfterCancellationIsCancelled() throws Exception {
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext());
        JobClient job = mock(JobClient.class);
        when(job.cancel()).thenReturn(CompletableFuture.completedFuture(null));

        runner.cancel();
        runner.onSubmitted(job);

        verify(job).cancel();
    }

    @Test
    void cancelBeforeSubmitDoesNotStartFlink() throws Exception {
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext());
        runner.cancel();
        assertTrue(runner.isCancelRequested());
        runner.execute(null);
    }
    @Test
    void failedCancellationIsNotReportedAsSuccessful() throws Exception {
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext());
        JobClient job = mock(JobClient.class);
        CompletableFuture<Void> rejected = new CompletableFuture<>();
        rejected.completeExceptionally(new IllegalStateException("Flink rejected cancellation"));
        when(job.cancel()).thenReturn(rejected);

        runner.onSubmitted(job);

        assertThrows(java.util.concurrent.ExecutionException.class, runner::cancel);
        assertFalse(runner.isCancellationConfirmed());
    }

}
