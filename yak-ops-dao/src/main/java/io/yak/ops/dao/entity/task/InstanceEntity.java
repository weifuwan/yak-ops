package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Workspace-owned logical Task run with an immutable Definition version. */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_instance")
public class InstanceEntity extends BaseEntity {
    private String workspaceId;
    private String definitionId;
    private Integer definitionVersion;
    private String taskType;
    private TaskTriggerType triggerType;
    private String requestId;
    private String requestFingerprint;
    private String resourceRevision;
    private TaskInstanceStatus status;
    private Integer stateVersion;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String failureCode;
}
