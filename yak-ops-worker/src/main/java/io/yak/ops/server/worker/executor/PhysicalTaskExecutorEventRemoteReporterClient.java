package io.yak.ops.server.worker.executor;

import io.yak.ops.extract.base.client.Clients;
import io.yak.ops.extract.master.IDataSyncTaskLifecycleOperator;
import io.yak.ops.extract.master.TaskExecutorEventRemoteReporterClient;
import io.yak.ops.extract.master.transportor.datasync.DataSyncTaskLifecycleEventRequest;
import io.yak.ops.extract.master.transportor.datasync.DataSyncTaskLifecycleEventResponse;
import io.yak.ops.task.executor.events.IReportableTaskExecutorLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorDispatchedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorFailedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorKilledLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorPausedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorRuntimeContextChangedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorStartedLifecycleEvent;
import io.yak.ops.task.executor.events.TaskExecutorSuccessLifecycleEvent;

import org.springframework.stereotype.Component;

@Component
public class PhysicalTaskExecutorEventRemoteReporterClient extends TaskExecutorEventRemoteReporterClient {

    @Override
    public void reportTaskExecutionEventToMaster(
            String masterAddress, IReportableTaskExecutorLifecycleEvent event) {
        if (event.getWorkflowInstanceId() > 0) {
            super.reportTaskExecutionEventToMaster(masterAddress, event);
            return;
        }

        DataSyncTaskLifecycleEventRequest request = toRequest(event);
        DataSyncTaskLifecycleEventResponse response = Clients
                .withService(IDataSyncTaskLifecycleOperator.class)
                .withHost(masterAddress)
                .reportLifecycleEvent(request);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException(response == null
                    ? "Master returned an empty DATA_SYNC lifecycle response"
                    : response.getMessage());
        }
    }

    private static DataSyncTaskLifecycleEventRequest toRequest(
            IReportableTaskExecutorLifecycleEvent event) {
        DataSyncTaskLifecycleEventRequest.DataSyncTaskLifecycleEventRequestBuilder builder =
                DataSyncTaskLifecycleEventRequest.builder()
                        .taskInstanceId(event.getTaskInstanceId())
                        .eventType(event.getType().name());

        if (event instanceof TaskExecutorDispatchedLifecycleEvent) {
            TaskExecutorDispatchedLifecycleEvent dispatched = (TaskExecutorDispatchedLifecycleEvent) event;
            builder.host(dispatched.getTaskInstanceHost());
        } else if (event instanceof TaskExecutorStartedLifecycleEvent) {
            TaskExecutorStartedLifecycleEvent started = (TaskExecutorStartedLifecycleEvent) event;
            builder.host(started.getTaskInstanceHost())
                    .startTime(started.getStartTime())
                    .logPath(started.getLogPath())
                    .executePath(started.getExecutePath());
        } else if (event instanceof TaskExecutorRuntimeContextChangedLifecycleEvent) {
            TaskExecutorRuntimeContextChangedLifecycleEvent changed =
                    (TaskExecutorRuntimeContextChangedLifecycleEvent) event;
            builder.host(changed.getTaskInstanceHost()).appIds(changed.getAppIds());
        } else if (event instanceof TaskExecutorPausedLifecycleEvent) {
            TaskExecutorPausedLifecycleEvent paused = (TaskExecutorPausedLifecycleEvent) event;
            builder.host(paused.getTaskInstanceHost()).endTime(paused.getEndTime());
        } else if (event instanceof TaskExecutorKilledLifecycleEvent) {
            TaskExecutorKilledLifecycleEvent killed = (TaskExecutorKilledLifecycleEvent) event;
            builder.host(killed.getTaskInstanceHost()).endTime(killed.getEndTime());
        } else if (event instanceof TaskExecutorFailedLifecycleEvent) {
            TaskExecutorFailedLifecycleEvent failed = (TaskExecutorFailedLifecycleEvent) event;
            builder.host(failed.getTaskInstanceHost())
                    .endTime(failed.getEndTime())
                    .appIds(failed.getAppIds());
        } else if (event instanceof TaskExecutorSuccessLifecycleEvent) {
            TaskExecutorSuccessLifecycleEvent success = (TaskExecutorSuccessLifecycleEvent) event;
            builder.host(success.getTaskInstanceHost()).endTime(success.getEndTime());
        }
        return builder.build();
    }
}
