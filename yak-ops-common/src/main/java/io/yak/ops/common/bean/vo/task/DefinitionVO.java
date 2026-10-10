package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 跨插件通用任务定义的只读响应，不包含专属参数或连接凭证。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Data
public class DefinitionVO {

    /** 稳定任务定义ID，可被未来工作流节点引用。 */
    private String id;

    /** 同一 Workspace 内唯一的任务名称。 */
    private String name;

    /** 插件类型，当前已注册 DATA_SYNC。 */
    private String taskType;

    /** 定义当前发布状态。 */
    private String status;

    /** 当前可执行定义版本。 */
    private Integer definitionVersion;

    /** 用户维护的任务说明。 */
    private String remark;

    /** 定义创建时间。 */
    private LocalDateTime createTime;

    /** 最近修改时间。 */
    private LocalDateTime updateTime;

    /** 最近修改人标识。 */
    private String updateBy;
}
