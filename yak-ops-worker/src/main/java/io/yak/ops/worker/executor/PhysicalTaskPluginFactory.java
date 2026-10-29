package io.yak.ops.worker.executor;

import io.yak.ops.worker.task.executor.TaskExecutionContext;

/** DolphinScheduler PhysicalTaskPluginFactory boundary: resolve one authorized physical task. */
public interface PhysicalTaskPluginFactory {
    PhysicalTask createPhysicalTask(TaskExecutionContext context);
}
