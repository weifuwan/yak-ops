package io.yak.ops.server.master.engine.executor;

import io.yak.ops.task.executor.listener.TaskExecutorLifecycleEventListener;

import org.springframework.stereotype.Component;

@Component
public class LogicTaskExecutorLifecycleEventListener extends TaskExecutorLifecycleEventListener {

    public LogicTaskExecutorLifecycleEventListener(
                                                   final LogicTaskExecutorContainerProvider logicTaskExecutorContainerDelegator,
                                                   final LogicTaskExecutorRepository logicTaskExecutorRepository,
                                                   final LogicTaskExecutorLifecycleEventReporter logicTaskExecutorEventReporter) {
        super(
                logicTaskExecutorContainerDelegator,
                logicTaskExecutorRepository,
                logicTaskExecutorEventReporter);
    }
}
