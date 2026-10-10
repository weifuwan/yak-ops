package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncExecutionEventLevel;
import io.yak.ops.common.enums.datasync.DataSyncExecutionEventType;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * DATA_SYNC 结构化产品事件在通用 Task Event 表中的兼容投影。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_event")
public class DataSyncExecutionEventEntity extends BaseEntity {

    /** 事件所属 Workspace ID。 */
    private String workspaceId;

    /** 所属 Execution 根实例 ID。 */
    @TableField("instance_id")
    private String executionId;

    /** 关联 Attempt ID；Execution 级事件为空。 */
    private String attemptId;

    /** 产品事件级别。 */
    private DataSyncExecutionEventLevel level;

    /** 生命周期事件类型。 */
    private DataSyncExecutionEventType eventType;

    /** 面向用户展示的脱敏事件说明。 */
    private String message;
}
