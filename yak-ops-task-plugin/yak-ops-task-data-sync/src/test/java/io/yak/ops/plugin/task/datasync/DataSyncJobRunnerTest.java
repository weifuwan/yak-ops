package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.datasync.execution.DataSyncExecutionCoordinator;

import org.junit.jupiter.api.Test;

class DataSyncJobRunnerTest {

    @Test
    void cancellationIsDelegatedToExecutionCoordinator() throws Exception {
        DataSyncExecutionCoordinator coordinator = mock(DataSyncExecutionCoordinator.class);
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext(), coordinator);

        runner.cancel();

        verify(coordinator).cancel();
    }

    @Test
    void cancelBeforeSubmitDoesNotStartFlink() throws Exception {
        DataSyncExecutionCoordinator coordinator = mock(DataSyncExecutionCoordinator.class);
        when(coordinator.isCancelRequested()).thenReturn(true);
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext(), coordinator);

        runner.execute(null);

        verify(coordinator).isCancelRequested();
    }

    @Test
    void cancellationStateIsExposedFromCoordinator() {
        DataSyncExecutionCoordinator coordinator = mock(DataSyncExecutionCoordinator.class);
        when(coordinator.isCancellationConfirmed()).thenReturn(true);
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext(), coordinator);

        assertTrue(runner.isCancellationConfirmed());
    }
}
