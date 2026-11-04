package io.yak.ops.task.executor.eventbus;

import io.yak.ops.task.executor.events.IReportableTaskExecutorLifecycleEvent;

public interface ITaskExecutorEventRemoteReporterClient {

    void reportTaskExecutionEventToMaster(final String masterAddress,
                                          final IReportableTaskExecutorLifecycleEvent reportableTaskExecutorLifecycleEvent);
}
