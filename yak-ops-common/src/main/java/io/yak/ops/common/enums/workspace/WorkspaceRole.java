package io.yak.ops.common.enums.workspace;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Workspace 成员角色；V1 只作为成员关系属性保存，不建立细粒度权限矩阵。
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
