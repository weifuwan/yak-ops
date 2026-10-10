package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 不可变的 Task Definition 可执行版本快照。
 *
 * <p>历史 Data Sync 只能回填迁移时的当前版本，不能推造已不可恢复的旧版本。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_definition_version")
public class DefinitionVersionEntity extends BaseEntity {

    /** 版本所属 Workspace。 */
    private String workspaceId;

    /** 稳定 Task Definition ID。 */
    private String definitionId;

    /** 当前插件 canonical type。 */
    private String taskType;

    /** 版本生成时的任务名称，仅用于历史展示。 */
    private String name;

    /** Task 内严格递增的可执行配置版本号。 */
    private Integer version;

    /** 脱敏的插件参数 JSON 快照；禁止包含连接凭证。 */
    @ToString.Exclude
    private String parametersSnapshot;
}
