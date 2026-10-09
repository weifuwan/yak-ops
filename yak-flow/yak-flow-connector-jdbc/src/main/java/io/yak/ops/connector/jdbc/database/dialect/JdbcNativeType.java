package io.yak.ops.connector.jdbc.database.dialect;

/** Native SQL type mapping and whether a target primary key can safely use it. */
public record JdbcNativeType(String ddl, String warning, boolean primaryKeySupported) {

    public JdbcNativeType {
        if (ddl == null || ddl.isBlank()) {
            throw new IllegalArgumentException("ddl must not be blank");
        }
        ddl = ddl.trim();
        warning = warning == null || warning.isBlank() ? null : warning.trim();
    }

    public static JdbcNativeType of(String ddl) {
        return new JdbcNativeType(ddl, null, true);
    }

    public static JdbcNativeType of(String ddl, String warning) {
        return new JdbcNativeType(ddl, warning, true);
    }

    public static JdbcNativeType nonKey(String ddl, String warning) {
        return new JdbcNativeType(ddl, warning, false);
    }
}
