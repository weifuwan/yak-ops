package io.yak.ops.connector.jdbc.database.dialect;

/**
 * Describes the native target SQL type and primary-key suitability of a logical column.
 *
 * @param ddl nonblank vendor DDL type declaration
 * @param warning optional warning about size or loss of target capabilities
 * @param primaryKeySupported whether the native type can safely be used in a primary key
 */
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

    /**
     * Creates a native type mapping that cannot safely become a target primary key.
     *
     * <p>Examples include overflow text/blob fallback types on databases with key
     * restrictions; the target planner must reject such primary-key columns.
     */
    public static JdbcNativeType nonKey(String ddl, String warning) {
        return new JdbcNativeType(ddl, warning, false);
    }
}
