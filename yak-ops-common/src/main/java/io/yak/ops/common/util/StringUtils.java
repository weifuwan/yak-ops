package io.yak.ops.common.util;

/**
 * 统一处理字符串空值、空白和基础归一化。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class StringUtils {

    private StringUtils() {}

    public static boolean isBlank(CharSequence value) {
        return value == null || value.toString().isBlank();
    }

    public static boolean isNotBlank(CharSequence value) {
        return !isBlank(value);
    }

    public static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
