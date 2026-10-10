package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.AttemptStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 通用 Task Attempt 只读投影；物理日志定位符不允许经由公开 HTTP 直接返回。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_attempt")
public class AttemptEntity extends BaseEntity {

    /** Attempt 所属 Workspace。 */
    private String workspaceId;

    /** 本次运行的通用 Task Instance ID。 */
    private String instanceId;

    /** 同一 Instance 中单调递增的 Attempt 序号，从1开始。 */
    private Integer attemptNo;

    /** 一次运行尝试的生命周期状态。 */
    private AttemptStatus status;

    /** 实际开始时间。 */
    private LocalDateTime startTime;

    /** 进入终态的完成时间。 */
    private LocalDateTime finishTime;

    /** 结构化错误码。 */
    private Integer errorCode;

    /** 已脱敏的执行错误消息。 */
    private String errorMessage;

    /** 受控日志存储定位符，未接入运行时前为空。 */
    @ToString.Exclude
    private String logUri;

    /** 后续分布式运行时的 Worker 身份。 */
    private String workerId;
}
