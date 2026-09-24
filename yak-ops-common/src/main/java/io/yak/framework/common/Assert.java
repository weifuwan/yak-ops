package io.yak.framework.common;

import java.util.Collection;

/**
 * 仅用于业务前置条件校验的轻量断言工具。
 */
public final class Assert {

    private Assert() {
    }

    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BusinessException(errorCode);
        }
    }

    public static void notNull(Object value, ErrorCode errorCode) {
        isTrue(value != null, errorCode);
    }

    public static void notBlank(String value, ErrorCode errorCode) {
        isTrue(value != null && !value.trim().isEmpty(), errorCode);
    }

    public static void notEmpty(Collection<?> value, ErrorCode errorCode) {
        isTrue(value != null && !value.isEmpty(), errorCode);
    }
}
