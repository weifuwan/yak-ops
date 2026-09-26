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
    INVALID_WORKSPACE(42003, "工作空间参数不合法");

    private final Integer code;
    private final String message;
}
