package io.yak.ops.server.master.engine.executor.plugin;

import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.server.master.exception.MasterTaskExecuteException;

public interface ILogicTask<T extends AbstractParameters> {

    void start() throws MasterTaskExecuteException;

    void pause() throws MasterTaskExecuteException;

    void kill() throws MasterTaskExecuteException;

    TaskExecutionStatus getTaskExecutionState();

    ITaskParameterDeserializer<T> getTaskParameterDeserializer();

}
