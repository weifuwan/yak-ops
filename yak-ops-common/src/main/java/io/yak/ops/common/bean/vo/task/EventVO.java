package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Task Instance结构化产品事件；不是JVM/Worker运行日志文件。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class EventVO {

    /** 产品事件ID。 */
    private String id;

    /** 所属Task Instance ID。 */
    private String instanceId;

    /** 相关Attempt ID，实例级事件为空。 */
    private String attemptId;

    /** 事件级别数字编码：1信息、2警告、3错误。 */
    private Integer level;

    /** 插件专属事件类型数字编码，需结合Task Type解释。 */
    private Integer eventType;

    /** 已脱敏的产品事件说明。 */
    private String message;

    /** 事件写入时间。 */
    private LocalDateTime createTime;
}
