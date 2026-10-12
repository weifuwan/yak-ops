package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Durable identity and dispatch state of one physical Task submission. */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_attempt")
public class AttemptEntity extends BaseEntity {
    /** Workspace scope of the owning business run. */
    private String workspaceId;
    /** Logical Task Instance that owns this physical submission. */
    private String instanceId;
    /** Submission ordinal within the Instance, starting at one. */
    private Integer attemptNumber;
    /** Durable dispatch or physical execution state. */
    private TaskAttemptStatus status;
    /** Dispatcher incarnation owning launch, absent until claimed. */
    private String ownerIncarnation;

    /** Opaque launch ownership token, never included in diagnostics. */
    @ToString.Exclude
    private String dispatchToken;

    /** Physical execution engine, absent before submission. */
    private String runtimeEngine;
    /** Engine-assigned Job identifier, absent until observed. */
    private String runtimeId;
    /** Opaque runtime diagnostic reference, absent until available. */
    private String logReference;
    /** Safe failure category, absent when no failure is recorded. */
    private String failureCode;
    /** Observed physical execution start time. */
    private LocalDateTime startTime;
    /** Confirmed physical execution termination time. */
    private LocalDateTime endTime;
}
