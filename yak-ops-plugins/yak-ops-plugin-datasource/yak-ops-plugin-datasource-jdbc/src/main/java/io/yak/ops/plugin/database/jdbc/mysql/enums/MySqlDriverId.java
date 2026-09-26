package io.yak.ops.plugin.database.jdbc.mysql.enums;

import java.util.Locale;

/**
 * MySQL JDBC Driver 选择标识，同时拥有对应隔离 Driver Runtime 的目录和 Driver 类名。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
public enum MySqlDriverId {

    /** 自动选择当前默认 Driver；当前等价于 MYSQL_8。 */
    AUTO("mysql/8", "com.mysql.cj.jdbc.Driver"),

    /** MySQL 8.x / 5.7.x 使用的 Connector/J 8.x Driver。 */
    MYSQL_8("mysql/8", "com.mysql.cj.jdbc.Driver"),

    /** MySQL 5.5.x / 5.6.x 使用的 Connector/J 5.1.x Driver。 */
    MYSQL_5("mysql/5", "com.mysql.jdbc.Driver");

    private final String driverDirectory;
    private final String driverClassName;

    MySqlDriverId(String driverDirectory, String driverClassName) {
        this.driverDirectory = driverDirectory;
        this.driverClassName = driverClassName;
    }

    public static MySqlDriverId parse(String value) {
        if (value == null || value.isBlank()) return AUTO;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public MySqlDriverId effective() {
        return this == AUTO ? MYSQL_8 : this;
    }

    public String runtimeId() {
        return effective().name();
    }

    public String driverDirectory() {
        return effective().driverDirectory;
    }

    public String driverClassName() {
        return effective().driverClassName;
    }
}
