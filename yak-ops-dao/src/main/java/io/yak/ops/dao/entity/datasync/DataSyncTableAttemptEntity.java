package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 保存一张表在一个 Root Execution 内的独立运行尝试，避免重复执行其他已成功的表。
 *
 * @author weifuwan
 * @since 2026-10-08
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_table_attempt")
public class DataSyncTableAttemptEntity extends BaseEntity {

    /** 工作空间隔离标识。 */
    private String workspaceId;

    /** 归属的 Table Execution ID。 */
    private String tableExecutionId;

    /** 此表内连续递增的尝试次数，从 1 开始。 */
    private Integer attemptNo;

    /** 当前尝试状态。 */
    private DataSyncAttemptStatus status;

    /** 当前尝试的源端读取计数。 */
    private Long readRows;

    /** 当前尝试的目标写入计数，不代表数据库事务提交证明。 */
    private Long writeRows;

    /** 开始运行的时间。 */
    private LocalDateTime startTime;

    /** 最终完成时间。 */
    private LocalDateTime finishTime;

    /** 失败的结构化错误码。 */
    private Integer errorCode;

    /** 失败时已经脱敏的错误信息。 */
    private String errorMessage;
}
