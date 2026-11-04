package io.yak.ops.server.worker.executor;

import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginManager;

import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskPluginFactory {

    public AbstractTask createPhysicalTask(final PhysicalTaskExecutor physicalTaskExecutor) {
        TaskChannel taskChannel = getTaskChannel(physicalTaskExecutor);
        return taskChannel.createTask(physicalTaskExecutor.getTaskExecutionContext());
    }

    public TaskChannel getTaskChannel(final PhysicalTaskExecutor physicalTaskExecutor) {
        final String taskType = physicalTaskExecutor.getTaskExecutionContext().getTaskType();
        return TaskPluginManager.getTaskChannel(taskType);
    }
}
