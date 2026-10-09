package io.yak.ops.plugin.database.jdbc.schema.dialect;

/**
 * JDBC 目标列原生类型规划结果。
 *
 * @param ddl 可直接放入 CREATE TABLE 列定义的原生类型片段
 * @param warning 安全放宽或元数据不足时的诊断提示；无提示时为空
 * @param primaryKeySupported 当前原生类型是否允许直接作为目标主键列
 * @author weifuwan
 * @since 2026-10-04
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

    public static JdbcNativeType nonKey(String ddl, String warning) {
        return new JdbcNativeType(ddl, warning, false);
    }
}
