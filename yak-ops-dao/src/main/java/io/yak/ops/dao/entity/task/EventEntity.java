package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Task Instance 产品生命周期事件只读投影，区别于运行时日志文件。
 *
 * <p>历史 DATA_SYNC 事件的数字 code 仅在插件域内有意义。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_event")
public class EventEntity extends BaseEntity {

    /** 事件所属 Workspace。 */
    private String workspaceId;

    /** 事件所属 Task Instance ID。 */
    private String instanceId;

    /** 可为空的具体 Attempt ID。 */
    private String attemptId;

    /** 历史产品事件级别编码，1信息、2警告、3错误。 */
    private Integer level;

    /** 插件专属事件类型编码，DATA_SYNC 历史使用1至12。 */
    private Integer eventType;

    /** 面向产品展示的脱敏事件摘要，不存逐行数据或密码。 */
    private String message;
}
