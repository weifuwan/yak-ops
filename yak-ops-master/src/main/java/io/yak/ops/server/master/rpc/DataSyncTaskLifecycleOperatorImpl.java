package io.yak.ops.server.master.rpc;

import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.extract.master.IDataSyncTaskLifecycleOperator;
import io.yak.ops.extract.master.transportor.datasync.DataSyncTaskLifecycleEventRequest;
import io.yak.ops.extract.master.transportor.datasync.DataSyncTaskLifecycleEventResponse;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Persists lifecycle events for standalone DATA_SYNC stream task instances (workflowInstanceId = 0). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncTaskLifecycleOperatorImpl implements IDataSyncTaskLifecycleOperator {

    private static final String TASK_TYPE = "DATA_SYNC";

    private final TaskInstanceDao taskInstanceDao;

    @Override
    public DataSyncTaskLifecycleEventResponse reportLifecycleEvent(DataSyncTaskLifecycleEventRequest request) {
        if (request == null || request.getTaskInstanceId() == null || StringUtils.isBlank(request.getEventType())) {
            return DataSyncTaskLifecycleEventResponse.rejected("Invalid data-sync lifecycle event");
        }

        TaskInstance current = taskInstanceDao.queryById(request.getTaskInstanceId());
        if (current == null) {
            return DataSyncTaskLifecycleEventResponse.rejected(
                    "Task instance not found: " + request.getTaskInstanceId());
        }
        if (!TASK_TYPE.equals(current.getTaskType())
                || current.getTaskExecuteType() == null
                || !current.getTaskExecuteType().name().equals("STREAM")) {
            return DataSyncTaskLifecycleEventResponse.rejected("Task instance is not a standalone DATA_SYNC stream task");
        }

        final String eventType = request.getEventType();
        final boolean terminalEvent = "SUCCESS".equals(eventType)
                || "FAILED".equals(eventType)
                || "KILLED".equals(eventType)
                || "PAUSED".equals(eventType);
        if (current.getState() != null && current.getState().isFinished() && !terminalEvent) {
            return DataSyncTaskLifecycleEventResponse.accepted();
        }

        TaskInstance update = new TaskInstance();
        update.setId(current.getId());
        if (StringUtils.isNotBlank(request.getHost())) {
            update.setHost(request.getHost());
        }
        switch (eventType) {
            case "DISPATCHED":
                update.setState(TaskExecutionStatus.DISPATCH);
                break;
            case "RUNNING":
                update.setState(TaskExecutionStatus.RUNNING_EXECUTION);
                if (request.getStartTime() != null) {
                    update.setStartTime(new Date(request.getStartTime()));
                }
                update.setLogPath(request.getLogPath());
                update.setExecutePath(request.getExecutePath());
                break;
            case "RUNTIME_CONTEXT_CHANGE":
                update.setAppLink(request.getAppIds());
                break;
            case "PAUSED":
                update.setState(TaskExecutionStatus.PAUSE);
                update.setEndTime(new Date(request.getEndTime() == null
                        ? System.currentTimeMillis() : request.getEndTime()));
                break;
            case "KILLED":
                update.setState(TaskExecutionStatus.KILL);
                update.setEndTime(new Date(request.getEndTime() == null
                        ? System.currentTimeMillis() : request.getEndTime()));
                break;
            case "FAILED":
                update.setState(TaskExecutionStatus.FAILURE);
                update.setEndTime(new Date(request.getEndTime() == null
                        ? System.currentTimeMillis() : request.getEndTime()));
                update.setAppLink(request.getAppIds());
                break;
            case "SUCCESS":
                update.setState(TaskExecutionStatus.SUCCESS);
                update.setEndTime(new Date(request.getEndTime() == null
                        ? System.currentTimeMillis() : request.getEndTime()));
                break;
            default:
                return DataSyncTaskLifecycleEventResponse.rejected("Unsupported lifecycle event: " + eventType);
        }

        taskInstanceDao.updateById(update);
        log.info("Persisted DATA_SYNC lifecycle event, taskInstanceId={}, eventType={}",
                request.getTaskInstanceId(), eventType);
        return DataSyncTaskLifecycleEventResponse.accepted();
    }
}
