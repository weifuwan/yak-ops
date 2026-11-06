package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import org.junit.jupiter.api.Test;

class DataSyncJobRunnerTest {

    @Test
    void cancelBeforeSubmitDoesNotStartFlink() throws Exception {
        DataSyncJobRunner runner = new DataSyncJobRunner(new TaskExecutionContext());
        runner.cancel();
        assertTrue(runner.isCancelRequested());
        runner.execute(null);
    }
}
