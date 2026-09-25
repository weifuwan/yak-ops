package io.yak.ops.common.enums.datasource;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据源连通状态。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@RequiredArgsConstructor
public enum DataSourceConnStatus {
    UNKNOWN("未测试"),
    CONNECTED("连接可用"),
    DISCONNECTED("连接不可用");

    private final String displayName;

    public static DataSourceConnStatus parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源连接状态不能为空");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
