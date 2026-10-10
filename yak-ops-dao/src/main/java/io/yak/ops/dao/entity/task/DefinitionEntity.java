package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 通用 Task Definition 持久化对象；不包含插件专属配置字段。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_definition")
public class DefinitionEntity extends BaseEntity {

    /** Task 所属 Workspace，所有访问必须按此隔离。 */
    private String workspaceId;

    /** Workspace 内唯一的任务名称。 */
    private String name;

    /** 插件注册的 canonical task type，例如 DATA_SYNC。 */
    private String taskType;

    /** 定义的上线/下线状态，和同步类型无关。 */
    private DefinitionStatus status;

    /** 当前可执行定义版本；仅可执行配置变化时递增。 */
    private Integer definitionVersion;

    /** 用户维护的任务说明，不属于可执行版本变更。 */
    private String remark;
}
