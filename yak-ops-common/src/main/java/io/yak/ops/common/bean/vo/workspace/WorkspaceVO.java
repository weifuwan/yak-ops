package io.yak.ops.common.bean.vo.workspace;

import io.yak.ops.common.enums.workspace.WorkspaceRole;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 返回当前用户可访问 Workspace 及其成员角色。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class WorkspaceVO {

    /** Workspace 主键 ID。 */
    private String id;

    /** Workspace 展示名称。 */
    private String name;

    /** Workspace 说明。 */
    private String description;

    /** 当前用户在该 Workspace 中的角色。 */
    private WorkspaceRole role;

    /** 当前用户角色展示名称。 */
    private String roleName;

    /** Workspace 创建时间。 */
    private LocalDateTime createTime;

    /** Workspace 最近更新时间。 */
    private LocalDateTime updateTime;
}
