package io.yak.ops.server.master.engine.executor;

import io.yak.ops.task.executor.eventbus.TaskExecutorLifecycleEventRemoteReporter;

import org.springframework.stereotype.Component;

@Component
public class LogicTaskExecutorLifecycleEventReporter extends TaskExecutorLifecycleEventRemoteReporter {

    public LogicTaskExecutorLifecycleEventReporter(
                                                   final LogicTaskExecutorEventRemoteReporterClient logicTaskExecutorEventRemoteReporterClient,
                                                   final LogicTaskExecutorRepository taskExecutorRepository) {
        super("LogicTaskExecutorLifecycleEventReporter", logicTaskExecutorEventRemoteReporterClient,
                taskExecutorRepository);
    }
}
