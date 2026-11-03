package io.yak.ops.plugin.datasource.api.lookup;

import java.util.Objects;

/** Database identifier parts, kept separate to prevent ambiguous SQL paths. */
public final class DataSourceTablePath {

    private final String catalog;
    private final String schema;
    private final String table;

    public DataSourceTablePath(String catalog, String schema, String table) {
        this.catalog = normalizeNamespace(catalog);
        this.schema = normalizeNamespace(schema);
        this.table = Objects.requireNonNull(table, "table").trim();
        if (this.table.isEmpty()) {
            throw new IllegalArgumentException("Table name must not be empty");
        }
    }

    private static String normalizeNamespace(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String catalog() {
        return catalog;
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataSourceTablePath)) {
            return false;
        }
        DataSourceTablePath that = (DataSourceTablePath) other;
        return Objects.equals(catalog, that.catalog)
                && Objects.equals(schema, that.schema)
                && table.equals(that.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalog, schema, table);
    }

    @Override
    public String toString() {
        return "DataSourceTablePath[catalog=" + catalog + ", schema=" + schema + ", table=" + table + "]";
    }
}
