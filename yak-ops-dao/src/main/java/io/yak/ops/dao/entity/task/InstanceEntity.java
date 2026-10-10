package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.InstanceStatus;
import io.yak.ops.common.enums.task.TriggerType;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 通用 Task Instance 的持久化只读投影；DATA_SYNC 继续复用相同的物理实例表。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_instance")
public class InstanceEntity extends BaseEntity {

    /** 当前实例所属 Workspace，查询必须显式限定。 */
    private String workspaceId;

    /** 插件 canonical type；历史同步任务为 DATA_SYNC。 */
    private String taskType;

    /** 运行时冻结的稳定 Task Definition ID。 */
    private String taskId;

    /** 本次实例启动时的任务名称快照。 */
    private String taskName;

    /** 本次实例使用的定义版本号。 */
    private Integer taskVersion;

    /** 未来 WorkflowInstance ID，独立任务为空。 */
    private String workflowInstanceId;

    /** 未来 Workflow 节点 ID，独立任务为空。 */
    private String workflowNodeId;

    /** 根触发来源，Retry Attempt 不能覆盖此值。 */
    private TriggerType triggerType;

    /** 调度定义 ID，手动运行为空。 */
    private String scheduleId;

    /** Quartz 计划触发时间，手动运行为空。 */
    private LocalDateTime scheduledFireTime;

    /** Task Instance 的当前生命周期状态。 */
    private InstanceStatus status;

    /** 当前或最终 Attempt 序号，从1开始。 */
    private Integer currentAttempt;

    /** 等待重试状态下的下次计划时间。 */
    private LocalDateTime nextRetryTime;

    /** 冻结的脱敏任务配置；不能通过通用查询接口泄露。 */
    @ToString.Exclude
    private String definitionSnapshot;

    /** 实例实际启动时间，等待中的任务可以为空。 */
    private LocalDateTime startTime;

    /** 实例进入终态的完成时间。 */
    private LocalDateTime finishTime;

    /** 失败原因的结构化错误码。 */
    private Integer errorCode;

    /** 已脱敏的失败原因，禁止含连接凭证。 */
    private String errorMessage;
}
