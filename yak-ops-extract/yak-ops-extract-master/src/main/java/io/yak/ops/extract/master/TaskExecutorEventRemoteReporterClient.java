package io.yak.ops.extract.master;

import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.task.executor.eventbus.ITaskExecutorEventRemoteReporterClient;
import io.yak.ops.task.executor.events.IReportableTaskExecutorLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorSuccessLifecycleEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskExecutorEventRemoteReporterClient implements ITaskExecutorEventRemoteReporterClient {

    @Override
    public void reportTaskExecutionEventToMaster(final String masterAddress,
                                                 final IReportableTaskExecutorLifecycleEvent taskExecutorLifecycleEvent) {
        try {
            taskExecutorLifecycleEvent.setLatestReportTime(System.currentTimeMillis());
            switch (taskExecutorLifecycleEvent.getType()) {
                case DISPATCHED:
                    reportTaskDispatchedEventToMaster(masterAddress,
                            (TaskExecutorDispatchedLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                case RUNNING:
                    reportTaskRunningEventToMaster(masterAddress,
                            (TaskExecutorStartedLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                case RUNTIME_CONTEXT_CHANGE:
                    reportTaskRuntimeContextChangeEventToMaster(masterAddress,
                            (TaskExecutorRuntimeContextChangedLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                case PAUSED:
                    reportTaskPausedEventToMaster(masterAddress,
                            (TaskExecutorPausedLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                case KILLED:
                    reportTaskKilledEventToMaster(masterAddress,
                            (TaskExecutorKilledLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                case FAILED:
                    reportTaskFailedEventToMaster(masterAddress,
                            (TaskExecutorFailedLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                case SUCCESS:
                    reportTaskSuccessEventToMaster(masterAddress,
                            (TaskExecutorSuccessLifecycleEvent) taskExecutorLifecycleEvent);
                    break;
                default:
                    log.warn("Unsupported TaskExecutionEvent: {}", taskExecutorLifecycleEvent);
            }
            log.info("Report: {} to master success", taskExecutorLifecycleEvent);
        } catch (Throwable throwable) {
            log.error("Report ITaskExecutorLifecycleEvent: {} to master failed", taskExecutorLifecycleEvent, throwable);
        }
    }

    private static void reportTaskDispatchedEventToMaster(final String masterAddress,
                                                          final TaskExecutorDispatchedLifecycleEvent taskExecutionDispatchedEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorDispatched(taskExecutionDispatchedEvent);
    }

    private static void reportTaskRunningEventToMaster(final String masterAddress,
                                                       final TaskExecutorStartedLifecycleEvent taskExecutionRunningEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorRunning(taskExecutionRunningEvent);
    }

    private static void reportTaskRuntimeContextChangeEventToMaster(final String masterAddress,
                                                                    final TaskExecutorRuntimeContextChangedLifecycleEvent taskExecutorLifecycleEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorRuntimeContextChanged(taskExecutorLifecycleEvent);
    }

    private static void reportTaskPausedEventToMaster(final String masterAddress,
                                                      final TaskExecutorPausedLifecycleEvent taskExecutionPausedEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorPaused(taskExecutionPausedEvent);
    }

    private static void reportTaskKilledEventToMaster(final String masterAddress,
                                                      final TaskExecutorKilledLifecycleEvent taskExecutionKilledEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorKilled(taskExecutionKilledEvent);
    }

    private static void reportTaskFailedEventToMaster(final String masterAddress,
                                                      final TaskExecutorFailedLifecycleEvent taskExecutionFailedEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorFailed(taskExecutionFailedEvent);
    }

    private static void reportTaskSuccessEventToMaster(final String masterAddress,
                                                       final TaskExecutorSuccessLifecycleEvent taskExecutionSuccessEvent) {
        Clients
                .withService(ITaskExecutorEventListener.class)
                .withHost(masterAddress)
                .onTaskExecutorSuccess(taskExecutionSuccessEvent);
    }
}
