package io.yak.ops.server.worker.executor;

import io.yak.ops.task.executor.listener.TaskExecutorLifecycleEventListener;

import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutorLifecycleEventListener extends TaskExecutorLifecycleEventListener {

    public PhysicalTaskExecutorLifecycleEventListener(
                                                      final PhysicalTaskExecutorContainerProvider physicalTaskExecutorContainerDelegator,
                                                      final PhysicalTaskExecutorRepository physicalTaskExecutorRepository,
                                                      final PhysicalTaskExecutorLifecycleEventReporter physicalTaskExecutorEventReporter) {
        super(
                physicalTaskExecutorContainerDelegator,
                physicalTaskExecutorRepository,
                physicalTaskExecutorEventReporter);
    }
}
