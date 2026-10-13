package io.yak.ops.connector.jdbc.database.connection;

import java.util.Locale;

/**
 * Selects an isolated MySQL JDBC driver runtime and its driver class.
 */
public enum MySqlDriverId {

    /** Selects the current default driver, equivalent to MYSQL_8. */
    AUTO("mysql/8", "com.mysql.cj.jdbc.NonRegisteringDriver"),

    /** Selects Connector/J 8.x for MySQL 8.x and 5.7.x. */
    MYSQL_8("mysql/8", "com.mysql.cj.jdbc.NonRegisteringDriver"),

    /** Selects Connector/J 5.1.x for MySQL 5.5.x and 5.6.x. */
    MYSQL_5("mysql/5", "com.mysql.jdbc.NonRegisteringDriver");

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
