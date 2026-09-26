package io.yak.ops.common.bean.dto.workspace;

import io.yak.ops.common.enums.workspace.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加 Workspace 成员时使用的输入契约。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class WorkspaceMemberCreateDTO {

    /** 待加入 Workspace 的用户 ID。 */
    @NotBlank(message = "成员用户ID不能为空")
    private String userId;

    /** 成员加入 Workspace 后的角色。 */
    @NotNull(message = "成员角色不能为空")
    private WorkspaceRole role;
}
