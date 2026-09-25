package io.yak.ops.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一响应构建所需的最小公共错误码集合。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    SUCCESS(200, "成功"),
    COMMON_FAIL(999, "失败"),
    PARAM_NOT_VALID(1001, "参数无效"),
    RESOURCE_DUPLICATION(10004, "数据已存在"),
    RESOURCE_NOT_EXISTS(10010, "资源不存在");

    private final Integer code;
    private final String message;
}
