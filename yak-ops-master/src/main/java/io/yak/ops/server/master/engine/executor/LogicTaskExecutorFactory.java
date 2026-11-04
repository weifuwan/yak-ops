package io.yak.ops.server.master.engine.executor;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.utils.LogUtils;
import io.yak.ops.server.master.engine.executor.plugin.LogicTaskPluginFactoryBuilder;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.ITaskExecutorFactory;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LogicTaskExecutorFactory implements ITaskExecutorFactory {

    private final LogicTaskPluginFactoryBuilder logicTaskPluginFactoryBuilder;

    public LogicTaskExecutorFactory(final LogicTaskPluginFactoryBuilder logicTaskPluginFactoryBuilder) {
        this.logicTaskPluginFactoryBuilder = logicTaskPluginFactoryBuilder;
    }

    @Override
    public ITaskExecutor createTaskExecutor(final TaskExecutionContext taskExecutionContext) {
        assemblyTaskLogPath(taskExecutionContext);

        final LogicTaskExecutorBuilder logicTaskExecutorBuilder = LogicTaskExecutorBuilder.builder()
                .taskExecutionContext(taskExecutionContext)
                .logicTaskPluginFactoryBuilder(logicTaskPluginFactoryBuilder)
                .build();
        return new LogicTaskExecutor(logicTaskExecutorBuilder);
    }

    private void assemblyTaskLogPath(final TaskExecutionContext taskExecutionContext) {
        taskExecutionContext.setLogPath(LogUtils.getTaskInstanceLogFullPath(taskExecutionContext));
    }

}
