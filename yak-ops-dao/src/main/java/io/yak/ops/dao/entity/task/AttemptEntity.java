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
    private String workspaceId;
    private String instanceId;
    private Integer attemptNumber;
    private TaskAttemptStatus status;
    private String ownerIncarnation;

    @ToString.Exclude
    private String dispatchToken;

    private String runtimeEngine;
    private String runtimeId;
    private String logReference;
    private String failureCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
