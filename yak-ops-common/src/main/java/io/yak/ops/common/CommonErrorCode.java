package io.yak.ops.common;

/**
 * 统一响应构建所需的最小公共错误码集合。
 */
public enum CommonErrorCode implements ErrorCode {
    SUCCESS(200, "成功"),
    COMMON_FAIL(999, "失败"),
    PARAM_NOT_VALID(1001, "参数无效"),
    RESOURCE_DUPLICATION(10004, "数据已存在"),
    RESOURCE_NOT_EXISTS(10010, "资源不存在");

    private final Integer code;
    private final String message;

    CommonErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
