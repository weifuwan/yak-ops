package io.yak.ops.business.workspace.enums;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Workspace 业务错误码。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Getter
@RequiredArgsConstructor
public enum WorkspaceErrorCode implements ErrorCode {
    NOT_FOUND(42001, "工作空间不存在"),
    ACCESS_DENIED(42002, "无权访问该工作空间"),
    INVALID_WORKSPACE(42003, "工作空间参数不合法"),
    MEMBER_ALREADY_EXISTS(42004, "用户已是该工作空间成员"),
    MEMBER_NOT_FOUND(42005, "工作空间成员不存在"),
    LAST_OWNER_REQUIRED(42006, "工作空间至少需要保留一个所有者"),
    ROLE_OPERATION_DENIED(42007, "无权执行该成员角色操作");

    private final Integer code;
    private final String message;
}
