package io.yak.ops.server.master.engine.executor;

import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.task.executor.ITaskExecutor;
import io.yak.ops.task.executor.TaskEngine;
import io.yak.ops.task.executor.dto.TaskExecutorDTO;
import io.yak.ops.task.executor.eventbus.ITaskExecutorLifecycleEventReporter;

import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LogicTaskEngineDelegator implements AutoCloseable {

    private final TaskEngine taskEngine;

    private final LogicTaskExecutorFactory logicTaskExecutorFactory;

    private final LogicTaskExecutorLifecycleEventReporter logicTaskExecutorEventReporter;

    public LogicTaskEngineDelegator(final LogicTaskEngineFactory logicTaskEngineFactory,
                                    final LogicTaskExecutorFactory logicTaskExecutorFactory,
                                    final LogicTaskExecutorLifecycleEventReporter logicTaskExecutorEventReporter) {
        this.logicTaskExecutorFactory = logicTaskExecutorFactory;
        this.taskEngine = logicTaskEngineFactory.createTaskEngine();
        this.logicTaskExecutorEventReporter = logicTaskExecutorEventReporter;
    }

    public void start() {
        taskEngine.start();
        logicTaskExecutorEventReporter.start();
        log.info("LogicTaskEngineDelegator started");
    }

    public void dispatchLogicTask(final TaskExecutionContext taskExecutionContext) {
        final ITaskExecutor taskExecutor = logicTaskExecutorFactory.createTaskExecutor(taskExecutionContext);
        taskEngine.submitTask(taskExecutor);
    }

    public void killLogicTask(final int taskInstanceId) {
        taskEngine.killTask(taskInstanceId);
    }

    public void pauseLogicTask(final int taskInstanceId) {
        taskEngine.pauseTask(taskInstanceId);
    }

    public List<TaskExecutorDTO> queryTaskExecutors() {
        return taskEngine.queryTaskExecutors();
    }

    public void ackLogicTaskExecutionEvent(final ITaskExecutorLifecycleEventReporter.TaskExecutorLifecycleEventAck taskExecutorLifecycleEventAck) {
        logicTaskExecutorEventReporter.receiveTaskExecutorLifecycleEventACK(taskExecutorLifecycleEventAck);
    }

    @Override
    public void close() {
        try (
                final TaskEngine ignore1 = taskEngine;
                final LogicTaskExecutorLifecycleEventReporter ignore2 = logicTaskExecutorEventReporter) {
            log.info("LogicTaskEngineDelegator closed");
        }
    }
}
