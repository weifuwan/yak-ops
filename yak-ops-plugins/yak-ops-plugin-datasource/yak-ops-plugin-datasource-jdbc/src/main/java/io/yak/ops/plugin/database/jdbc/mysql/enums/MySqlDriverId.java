package io.yak.ops.plugin.database.jdbc.mysql.enums;

import java.util.Locale;

/**
 * MySQL JDBC Driver 选择标识，只描述用户选择，不负责具体 Driver Jar 的加载与隔离。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public enum MySqlDriverId {

    /** 使用当前默认 MySQL Driver；V1 等价于 MYSQL_8。 */
    AUTO,

    /** MySQL 8.x / 5.7.x 连接使用的 Connector/J 8.x 系列。 */
    MYSQL_8,

    /** MySQL 5.5.x / 5.6.x 连接使用的 Connector/J 5.1.x 系列。 */
    MYSQL_5;

    public static MySqlDriverId parse(String value) {
        if (value == null || value.isBlank()) return AUTO;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
