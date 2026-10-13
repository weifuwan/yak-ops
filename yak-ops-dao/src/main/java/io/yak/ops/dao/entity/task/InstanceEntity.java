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
    /** Workspace owning this run and its execution data. */
    private String workspaceId;
    /** Task Definition identifier pinned at admission. */
    private String definitionId;
    /** Immutable Definition version used for this execution. */
    private Integer definitionVersion;
    /** Registered Task type captured from the pinned Definition. */
    private String taskType;
    /** Debug diagnostics captured when this Instance was admitted. */
    private boolean debug;
    /** Submission namespace for the idempotency key. */
    private TaskTriggerType triggerType;
    /** Caller-stable identity of this execution request. */
    private String requestId;
    /** Non-secret intent fingerprint used to identify conflicting retries. */
    private String requestFingerprint;
    /** Admitted resource binding revision verified before launch. */
    private String resourceRevision;
    /** Business lifecycle independent of the physical Job status. */
    private TaskInstanceStatus status;
    /** Current embedded owner of the physical submission. */
    private String ownerIncarnation;
    /** One-time dispatch token. */
    @ToString.Exclude
    private String dispatchToken;
    /** Engine responsible for the physical Job. */
    private String runtimeEngine;
    /** Engine-assigned Job identifier. */
    private String runtimeId;
    /** Relative path to this Instance's execution log file. */
    private String logPath;
    /** Optimistic version guarding competing state transitions. */
    private Integer stateVersion;
    /** Observed start time, absent until execution begins. */
    private LocalDateTime startTime;
    /** Confirmed terminal time, absent while execution is active. */
    private LocalDateTime endTime;
    /** Safe failure category without credentials, absent while healthy. */
    private String failureCode;
}
