package io.yak.ops.common.bean.vo.workspace;

import io.yak.ops.common.enums.workspace.WorkspaceRole;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 返回 Workspace 与用户之间的成员关系。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class WorkspaceMemberVO {

    /** 成员用户 ID。 */
    private String userId;

    /** 成员在 Workspace 中的角色。 */
    private WorkspaceRole role;

    /** 成员角色展示名称。 */
    private String roleName;

    /** 用户加入 Workspace 的时间。 */
    private LocalDateTime joinedAt;
}
