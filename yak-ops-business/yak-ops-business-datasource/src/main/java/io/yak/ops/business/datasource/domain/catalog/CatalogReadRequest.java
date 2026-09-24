package io.yak.ops.business.datasource.domain.catalog;

import java.util.List;
import java.util.Objects;

/** Typed Catalog read request used inside the Business Datasource boundary. */
public record CatalogReadRequest(ReadMode mode, String tablePath, String sql, List<Variable> variables) {

    public CatalogReadRequest {
        mode = Objects.requireNonNull(mode, "Catalog 读取模式不能为空");
        tablePath = normalizeNullable(tablePath);
        sql = normalizeNullable(sql);
        variables = variables == null ? List.of() : List.copyOf(variables);
        if (variables.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Catalog 变量不能为空");
        }
        if (mode == ReadMode.TABLE && tablePath == null) {
            throw new IllegalArgumentException("表模式下 table_path 不能为空");
        }
        if (mode == ReadMode.SQL && sql == null) {
            throw new IllegalArgumentException("SQL 模式下 query 不能为空");
        }
    }

    public boolean sqlMode() {
        return mode == ReadMode.SQL;
    }

    public enum ReadMode {
        TABLE,
        SQL
    }

    public record Variable(String name, String value) {
        public Variable {
            name = requireText(name, "Catalog 变量名不能为空");
            value = normalizeNullable(value);
        }
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
