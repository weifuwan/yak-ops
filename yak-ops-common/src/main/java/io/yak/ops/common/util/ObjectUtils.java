package io.yak.ops.common.util;

import java.util.Objects;

/**
 * 统一表达对象空值判断，供跨模块基础代码复用。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class ObjectUtils {

    private ObjectUtils() {}

    public static boolean isNull(Object value) {
        return value == null;
    }

    public static boolean isNotNull(Object value) {
        return value != null;
    }

    public static <T> T requireNonNull(T value, String message) {
        return Objects.requireNonNull(value, message);
    }
}
