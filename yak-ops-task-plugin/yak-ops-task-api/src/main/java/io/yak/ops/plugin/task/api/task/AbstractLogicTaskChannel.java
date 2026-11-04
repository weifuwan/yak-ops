package io.yak.ops.plugin.task.api.task;

import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.ILogicTaskChannel;
import io.yak.ops.plugin.task.api.TaskExecutionContext;

public abstract class AbstractLogicTaskChannel implements ILogicTaskChannel {

    @Override
    public AbstractTask createTask(TaskExecutionContext taskRequest) {
        throw new UnsupportedOperationException("createTask is not supported");
    }

}
