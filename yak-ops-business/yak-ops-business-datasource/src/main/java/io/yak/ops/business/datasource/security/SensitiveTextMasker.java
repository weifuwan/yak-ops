package io.yak.ops.business.datasource.security;

/**
 * 遮罩 JDBC URL 和错误消息等用户可见文本中可能出现的连接凭证。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class SensitiveTextMasker {

    public static final String MASKED_VALUE = "******";

    private SensitiveTextMasker() {}

    public static String mask(String value) {
        if (value == null || value.isEmpty()) return value;
        String masked =
                value.replaceAll("(?i)((?:^|[?&;])(?:password|pwd|token|secret)=)[^&;\\s]*", "$1" + MASKED_VALUE);
        return masked.replaceAll("(?i)(://[^:/?#\\s]+:)[^@/?#\\s]+@", "$1" + MASKED_VALUE + "@");
    }
}
