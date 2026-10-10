package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncAttemptStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * DATA_SYNC Attempt 对通用 Task Attempt 表的投影，历史 Execution ID 映射为 instance_id。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_attempt")
public class DataSyncAttemptEntity extends BaseEntity {

    /** Attempt 所属 Workspace ID。 */
    private String workspaceId;

    /** 所属 Execution 根实例 ID。 */
    @TableField("instance_id")
    private String executionId;

    /** Execution 内 Attempt 序号，从 1 开始。 */
    private Integer attemptNo;

    /** Attempt 生命周期状态。 */
    private DataSyncAttemptStatus status;

    /** 本 Attempt 累计读取行数。 */
    private Long readRows;

    /** 本 Attempt 累计写入行数。 */
    private Long writeRows;

    /** 本 Attempt 实际开始时间。 */
    private LocalDateTime startTime;

    /** 本 Attempt 进入终态的完成时间。 */
    private LocalDateTime finishTime;

    /** 本 Attempt 失败时的结构化错误码。 */
    private Integer errorCode;

    /** 本 Attempt 失败时的脱敏错误信息。 */
    private String errorMessage;

    /** 受控日志定位符；执行引擎未接入前保持 null，不能返回虚构路径。 */
    @ToString.Exclude
    private String logUri;

    /** 后续分布式 Worker 的标识；历史记录为空。 */
    private String workerId;
}
