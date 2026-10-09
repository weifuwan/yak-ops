package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncTableExecutionStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_sync_table_execution 表，保存 Root Execution 内每条冻结 Route 的执行身份与状态。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_table_execution")
public class DataSyncTableExecutionEntity extends BaseEntity {

    /** Table Execution 所属 Workspace ID。 */
    private String workspaceId;

    /** 所属 Root Execution ID。 */
    private String executionId;

    /** Root Execution 创建时冻结的稳定 Route ID。 */
    private String routeId;

    /** Root Execution 内冻结的 Route 顺序，从 0 开始。 */
    private Integer routeOrder;

    /** 表级执行状态；PR2 创建时固定为 PLANNED。 */
    private DataSyncTableExecutionStatus status;

    /** 表级当前 Attempt 序号；PR2 尚未开始 Runtime，因此创建时为 0。 */
    private Integer currentAttempt;

    /** 当前或最终表级 Attempt 的读取行数镜像。 */
    private Long readRows;

    /** 当前或最终表级 Attempt 的写入行数镜像。 */
    private Long writeRows;

    /** 表级 Runtime 实际开始时间；尚未运行时为空。 */
    private LocalDateTime startTime;

    /** 表级执行进入终态时间；尚未结束时为空。 */
    private LocalDateTime finishTime;

    /** 表级执行失败时的结构化错误码。 */
    private Integer errorCode;

    /** 表级执行失败时的脱敏错误消息。 */
    private String errorMessage;
}
