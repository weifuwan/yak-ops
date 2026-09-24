package io.yak.ops.business.datasource.security;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.springframework.stereotype.Component;

/** Masks credentials embedded in user-facing text such as JDBC URLs and error messages. */
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
