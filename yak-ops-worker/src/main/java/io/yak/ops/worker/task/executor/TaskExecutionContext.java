package io.yak.ops.worker.task.executor;

import java.util.Objects;

/** Worker-local identity of one physical Task; business ownership remains in InstanceLifecycleService. */
public final class TaskExecutionContext {
    private final String workspaceId;
    private final String taskInstanceId;
    private String taskType;
    private String taskName;
    private String workflowInstanceId;
    private long startTime;
    private long endTime;

    public TaskExecutionContext(String workspaceId, String taskInstanceId) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.taskInstanceId = Objects.requireNonNull(taskInstanceId, "taskInstanceId");
    }

    public String getWorkspaceId() { return workspaceId; }
    public String getTaskInstanceId() { return taskInstanceId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(String workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
}
