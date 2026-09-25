package io.yak.ops.common.enums.datasource;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据源所属运行环境。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@RequiredArgsConstructor
public enum DataSourceEnvironment {
    DEVELOP("开发"),
    TEST("测试"),
    PROD("生产");

    private final String displayName;

    public static DataSourceEnvironment parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEVELOP;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("DEV".equals(normalized) || "DEVELOPMENT".equals(normalized)) {
            normalized = "DEVELOP";
        }
        if ("PRODUCTION".equals(normalized)) {
            normalized = "PROD";
        }

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的运行环境：" + value, exception);
        }
    }
}
