package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.apache.flink.core.execution.JobClient;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
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
}
