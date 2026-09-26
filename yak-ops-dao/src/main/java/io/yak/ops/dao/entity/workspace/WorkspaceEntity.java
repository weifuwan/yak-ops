package io.yak.ops.dao.entity.workspace;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_workspace 表，保存 Workspace 自身信息。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_workspace")
public class WorkspaceEntity extends BaseEntity {

    /** Workspace 展示名称。 */
    private String name;

    /** Workspace 说明，可为空。 */
    private String description;
}
