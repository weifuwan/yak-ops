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

    /** 遮罩 URL、连接串、JSON 和错误文本中的常见凭证。 */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) return value;
        String masked = value.replaceAll(
                "(?i)((?:^|[?&;\\s])(?:password|pwd|token|secret|access[_-]?key|secret[_-]?key|passphrase)=)[^&;\\s]*",
                "$1" + MASKED_VALUE);
        masked = masked.replaceAll(
                "(?i)(\\x22(?:password|pwd|token|secret|access[_-]?key|secret[_-]?key|secretAccessKey|passphrase|privateKey|privateKeyContent|privateKeyPassphrase)\\x22\\s*:\\s*\\x22)[^\\x22]*(\\x22)",
                "$1" + MASKED_VALUE + "$2");
        masked = masked.replaceAll(
                "(?i)((?:authorization|proxy-authorization)\\s*[:=]\\s*(?:(?:bearer|basic)\\s+)?)[^\\s,;]+",
                "$1" + MASKED_VALUE);
        return masked.replaceAll("(?i)(://[^:/?#\\s]+:)[^@/?#\\s]+@", "$1" + MASKED_VALUE + "@");
    }
}
