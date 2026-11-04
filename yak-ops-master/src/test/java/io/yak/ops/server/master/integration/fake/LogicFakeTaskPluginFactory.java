package io.yak.ops.server.master.integration.fake;

import io.yak.ops.server.master.engine.executor.plugin.ILogicTaskPluginFactory;
import io.yak.ops.task.executor.ITaskExecutor;

import org.springframework.stereotype.Component;

import com.google.common.annotations.VisibleForTesting;

@Component
@VisibleForTesting
public class LogicFakeTaskPluginFactory implements ILogicTaskPluginFactory<LogicFakeTask> {

    @Override
    public LogicFakeTask createLogicTask(final ITaskExecutor taskExecutor) {
        return new LogicFakeTask(taskExecutor.getTaskExecutionContext());
    }

    @Override
    public String getTaskType() {
        return LogicFakeTaskChannelFactory.NAME;
    }
}
