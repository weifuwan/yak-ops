package io.yak.ops.business.datasource.security;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.springframework.stereotype.Component;

/**
 * 遮罩 JDBC URL 和错误消息等用户可见文本中可能出现的连接凭证。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class SensitiveTextMasker {

    public static final String MASKED_VALUE = "******";

    public String mask(String value) {
        if (value == null || value.isEmpty()) return value;
        String masked =
                value.replaceAll("(?i)((?:^|[?&;])(?:password|pwd|token|secret)=)[^&;\\s]*", "$1" + MASKED_VALUE);
        return masked.replaceAll("(?i)(://[^:/?#\\s]+:)[^@/?#\\s]+@", "$1" + MASKED_VALUE + "@");
    }
}
