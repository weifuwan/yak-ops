package io.yak.ops.dao.task;

/** Published within the release transaction; listeners wake waiting queues after commit. */
public final class TaskGroupSlotReleasedEvent {
    private final String instanceId;

    public TaskGroupSlotReleasedEvent(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
