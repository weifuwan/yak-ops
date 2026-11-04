package io.yak.ops.server.worker.executor;

import io.yak.ops.task.executor.eventbus.TaskExecutorLifecycleEventRemoteReporter;

import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutorLifecycleEventReporter extends TaskExecutorLifecycleEventRemoteReporter {

    public PhysicalTaskExecutorLifecycleEventReporter(
                                                      final PhysicalTaskExecutorEventRemoteReporterClient physicalTaskExecutorEventRemoteReporterClient,
                                                      final PhysicalTaskExecutorRepository taskExecutorRepository) {
        super("PhysicalTaskExecutorLifecycleEventReporter", physicalTaskExecutorEventRemoteReporterClient,
                taskExecutorRepository);
    }
}
