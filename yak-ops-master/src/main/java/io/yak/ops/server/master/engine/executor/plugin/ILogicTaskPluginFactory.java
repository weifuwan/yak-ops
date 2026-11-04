package io.yak.ops.server.master.engine.executor.plugin;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.server.master.exception.LogicTaskInitializeException;
import io.yak.ops.task.executor.ITaskExecutor;

public interface ILogicTaskPluginFactory<T extends ILogicTask<? extends AbstractParameters>> {

    T createLogicTask(final ITaskExecutor taskExecutor) throws LogicTaskInitializeException;

    String getTaskType();

}
