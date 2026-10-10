package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 插件无关的Task Instance运行结果视图，不包含配置快照和数据源凭证。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class InstanceVO {

    /** 一次Task运行的稳定Instance ID。 */
    private String id;

    /** 插件canonical type，历史同步实例为DATA_SYNC。 */
    private String taskType;

    /** 关联任务定义ID。 */
    private String taskId;

    /** 启动时冻结的任务名称。 */
    private String taskName;

    /** 执行时固定的定义版本。 */
    private Integer taskVersion;

    /** 所属WorkflowInstance；独立运行为空。 */
    private String workflowInstanceId;

    /** 所属Workflow节点；独立运行为空。 */
    private String workflowNodeId;

    /** 根触发类型；Retry不改变此值。 */
    private String triggerType;

    /** 触发来源Schedule ID，手动运行为空。 */
    private String scheduleId;

    /** 调度期望触发时间，手动运行为空。 */
    private LocalDateTime scheduledFireTime;

    /** 当前实例状态。 */
    private String status;

    /** 当前或最终Attempt序号。 */
    private Integer currentAttempt;

    /** 处于等待重试状态时的计划重试时间。 */
    private LocalDateTime nextRetryTime;

    /** 实际开始时间。 */
    private LocalDateTime startTime;

    /** 进入终态的时间。 */
    private LocalDateTime finishTime;

    /** 结构化失败码。 */
    private Integer errorCode;

    /** 已脱敏的错误信息。 */
    private String errorMessage;

    /** 根实例创建时间。 */
    private LocalDateTime createTime;

    /** 根实例更新时间。 */
    private LocalDateTime updateTime;
}
