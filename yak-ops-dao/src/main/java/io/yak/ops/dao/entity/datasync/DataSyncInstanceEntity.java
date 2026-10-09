package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_sync_instance 表，承载一次数据同步 Execution 的稳定根身份与历史状态。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_instance")
public class DataSyncInstanceEntity extends BaseEntity {

    /** 实例所属 Workspace ID。 */
    private String workspaceId;

    /** 来源任务 ID。 */
    private String taskId;

    /** 实例创建时的任务名称快照。 */
    private String taskName;

    /** 实例采用的任务定义版本。 */
    private Integer taskVersion;

    /** 实例同步类型，独立固化以支持任务删除后的历史查询。 */
    private DataSyncType syncType;

    /** Execution 根触发方式；Retry Attempt 不改变该来源。 */
    private DataSyncTriggerType triggerType;

    /** 本次 Execution 允许的最大 Attempt 总数，包含首次执行。 */
    private Integer maxAttempts;

    /** Attempt 失败后的固定等待秒数。 */
    private Integer backoffSeconds;

    /** 当前或最终 Attempt 序号，从 1 开始。 */
    private Integer currentAttempt;

    /** RETRY_WAITING 状态下下一次 Attempt 的计划时间。 */
    private LocalDateTime nextRetryTime;

    /** 实例生命周期状态。 */
    private DataSyncInstanceStatus status;

    /** Execution 固定的脱敏任务定义快照，未来所有 Retry Attempt 必须复用，禁止包含任何数据源凭证。 */
    @ToString.Exclude
    private String definitionSnapshot;

    /** 累计读取行数。 */
    private Long readRows;

    /** 累计写入行数。 */
    private Long writeRows;

    /** 实际开始时间。 */
    private LocalDateTime startTime;

    /** 进入终态的完成时间。 */
    private LocalDateTime finishTime;

    /** 失败时的结构化错误码。 */
    private Integer errorCode;

    /** 失败时的脱敏错误消息。 */
    private String errorMessage;
}
