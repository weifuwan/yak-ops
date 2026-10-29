package io.yak.ops.worker.task.executor;

import java.util.Objects;

/** Worker-local identity of one physical Task; business ownership stays with the Master. */
public final class TaskExecutionContext {
    private final String workspaceId;
    private final String taskInstanceId;
    private String taskType;
    private String taskName;
    private String workflowInstanceId;
    private String workflowInstanceName;
    private String projectCode;
    private String logPath;
    private long startTime;
    private long endTime;

    public TaskExecutionContext(String workspaceId, String taskInstanceId) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.taskInstanceId = Objects.requireNonNull(taskInstanceId, "taskInstanceId");
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getTaskInstanceId() {
        return taskInstanceId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public String getWorkflowInstanceName() {
        return workflowInstanceName;
    }

    public void setWorkflowInstanceName(String name) {
        workflowInstanceName = name;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String value) {
        projectCode = value;
    }

    public void setWorkflowInstanceId(String id) {
        workflowInstanceId = id;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String path) {
        logPath = path;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long time) {
        startTime = time;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long time) {
        endTime = time;
    }
}
