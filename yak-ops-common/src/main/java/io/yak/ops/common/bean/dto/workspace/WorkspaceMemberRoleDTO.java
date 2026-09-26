package io.yak.ops.common.bean.dto.workspace;

import io.yak.ops.common.enums.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改 Workspace 成员角色时使用的输入契约。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class WorkspaceMemberRoleDTO {

    /** 修改后的成员角色。 */
    @NotNull(message = "成员角色不能为空")
    private WorkspaceRole role;
}
