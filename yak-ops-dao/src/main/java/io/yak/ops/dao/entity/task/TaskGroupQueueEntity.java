package io.yak.ops.dao.entity.task;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** A DolphinScheduler TaskGroupQueue slot, resolved to its workspace resource group. */
@Getter
@Setter
public class TaskGroupQueueEntity extends BaseEntity {
    private String workspaceId;
    private String resourceType;
    private String resourceKey;
    private String instanceId;
    /** Higher priority enters a free TaskGroup before lower priority; defaults to FIFO. */
    private int priority;
}
