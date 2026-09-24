package io.yak.ops.business.datasource.domain.catalog;

/** Complete business-side locator for one table. */
public record CatalogTablePath(String database, String schema, String table) {

    public CatalogTablePath {
        database = normalizeNullable(database);
        schema = normalizeNullable(schema);
        table = requireText(table, "Catalog 表名不能为空");
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
