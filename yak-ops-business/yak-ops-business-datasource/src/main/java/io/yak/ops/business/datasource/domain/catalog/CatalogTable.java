package io.yak.ops.business.datasource.domain.catalog;

/** Business-owned table metadata. */
public record CatalogTable(String database, String schema, String name, String type, String remarks) {

    public CatalogTable {
        database = normalizeNullable(database);
        schema = normalizeNullable(schema);
        name = requireText(name, "Catalog 表名不能为空");
        type = normalizeNullable(type);
        remarks = normalizeNullable(remarks);
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
