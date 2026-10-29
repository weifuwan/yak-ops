package io.yak.ops.worker.executor;

import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import java.util.concurrent.CompletionStage;

/**
 * Embedded adaptation of DolphinScheduler AbstractTask.handle/cancel.
 * The existing Yak Ops Task SPI remains the only plugin SPI.
 */
public interface PhysicalTask {
    CompletionStage<TaskOutcome> handle();

    void cancel();
}
