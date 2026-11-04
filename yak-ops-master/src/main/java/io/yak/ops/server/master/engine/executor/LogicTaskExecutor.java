package io.yak.ops.server.master.engine.executor;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.server.master.engine.executor.plugin.ILogicTask;
import io.yak.ops.server.master.engine.executor.plugin.LogicTaskPluginFactoryBuilder;
import io.yak.ops.task.executor.AbstractTaskExecutor;
import io.yak.ops.task.executor.TaskExecutorState;
import io.yak.ops.task.executor.TaskExecutorStateMappings;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogicTaskExecutor extends AbstractTaskExecutor {

    private ILogicTask<? extends AbstractParameters> logicTask;

    private final LogicTaskPluginFactoryBuilder logicTaskPluginFactoryBuilder;

    public LogicTaskExecutor(final LogicTaskExecutorBuilder logicTaskExecutorBuilder) {
        super(logicTaskExecutorBuilder.getTaskExecutionContext(), logicTaskExecutorBuilder.getTaskExecutorEventBus());
        this.logicTaskPluginFactoryBuilder = logicTaskExecutorBuilder.getLogicTaskPluginFactoryBuilder();
    }

    @Override
    protected TaskExecutorState doTrackTaskPluginStatus() {
        return TaskExecutorStateMappings.mapState(logicTask.getTaskExecutionState());
    }

    @SneakyThrows
    @Override
    protected void initializeTaskPlugin() {
        logicTask = logicTaskPluginFactoryBuilder
                .createILogicTaskPluginFactory(taskExecutionContext.getTaskType())
                .createLogicTask(this);
        log.info("Initialized task plugin instance: {} successfully", taskExecutionContext.getTaskType());
    }

    @SneakyThrows
    @Override
    protected void doTriggerTaskPlugin() {
        logicTask.start();
    }

    @SneakyThrows
    @Override
    public void pause() {
        // todo: judge the status, whether the task is running, we should support to pause the task which is not running
        // if the status is initialized, then we can directly change to paused
        if (logicTask != null) {
            logicTask.pause();
        }
    }

    @SneakyThrows
    @Override
    public void kill() {
        if (logicTask != null) {
            logicTask.kill();
        }
    }

    @Override
    public void finalizeTask() {
        // do nothing for now
    }

    @Override
    public String toString() {
        return "LogicTaskExecutor{" +
                "id=" + taskExecutionContext.getTaskInstanceId() +
                ", name=" + taskExecutionContext.getTaskName() +
                ", stat=" + taskExecutorState.get() +
                '}';
    }
}
