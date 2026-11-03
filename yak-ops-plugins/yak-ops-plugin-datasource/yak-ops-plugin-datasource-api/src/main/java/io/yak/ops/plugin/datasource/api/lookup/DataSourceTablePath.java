package io.yak.ops.plugin.datasource.api.lookup;

import java.util.Objects;

/** Database identifier parts, kept separate to prevent ambiguous SQL paths. */
public record DataSourceTablePath(String catalog, String schema, String table) {
    public DataSourceTablePath {
        catalog = catalog == null || catalog.isBlank() ? null : catalog.trim();
        schema = schema == null || schema.isBlank() ? null : schema.trim();
        table = Objects.requireNonNull(table, "table").trim();
        if (table.isEmpty()) throw new IllegalArgumentException("Table name must not be empty");
    }
}
