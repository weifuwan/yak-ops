package io.yak.ops.common.util;

/**
 * 通用敏感文本脱敏工具。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class SensitiveUtils {

    public static final String MASKED_VALUE = "******";

    private SensitiveUtils() {}

    /** 遮罩 URL、连接串和错误文本中的常见凭证。 */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) return value;
        String masked =
                value.replaceAll("(?i)((?:^|[?&;])(?:password|pwd|token|secret)=)[^&;\\s]*", "$1" + MASKED_VALUE);
        return masked.replaceAll("(?i)(://[^:/?#\\s]+:)[^@/?#\\s]+@", "$1" + MASKED_VALUE + "@");
    }
}
