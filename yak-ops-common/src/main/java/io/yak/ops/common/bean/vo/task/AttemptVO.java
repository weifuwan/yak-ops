package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 通用Attempt历史响应；只返回是否存在日志引用，绝不暴露受控存储URI。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class AttemptVO {

    /** 一次尝试的稳定ID。 */
    private String id;

    /** 根Task Instance ID。 */
    private String instanceId;

    /** 同一Instance下从1递增的序号。 */
    private Integer attemptNo;

    /** 本次Attempt的终态或活动状态。 */
    private String status;

    /** 是否保存了受控的运行日志引用；不表示日志文件一定可读取。 */
    private Boolean logAvailable;

    /** 实际开始时间。 */
    private LocalDateTime startTime;

    /** 实际结束时间。 */
    private LocalDateTime finishTime;

    /** 结构化错误码。 */
    private Integer errorCode;

    /** 脱敏错误信息。 */
    private String errorMessage;

    /** Attempt创建时间。 */
    private LocalDateTime createTime;
}
