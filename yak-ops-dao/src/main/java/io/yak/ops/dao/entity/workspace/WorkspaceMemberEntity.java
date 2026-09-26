package io.yak.ops.dao.entity.workspace;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.workspace.WorkspaceRole;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_workspace_member 表，保存用户与 Workspace 的成员关系。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_workspace_member")
public class WorkspaceMemberEntity extends BaseEntity {

    /** 成员关系所属 Workspace ID。 */
    private String workspaceId;

    /** 成员用户 ID。 */
    private String userId;

    /** 用户在 Workspace 中的角色。 */
    private WorkspaceRole role;
}
