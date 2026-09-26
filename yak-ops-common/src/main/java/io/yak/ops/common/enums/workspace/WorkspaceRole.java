package io.yak.ops.common.enums.workspace;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Workspace 成员角色；成员管理只使用 OWNER / ADMIN / MEMBER 的最小权限边界，不建立通用 RBAC。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Getter
@RequiredArgsConstructor
public enum WorkspaceRole {
    OWNER(1, "所有者"),
    ADMIN(2, "管理员"),
    MEMBER(3, "成员");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
