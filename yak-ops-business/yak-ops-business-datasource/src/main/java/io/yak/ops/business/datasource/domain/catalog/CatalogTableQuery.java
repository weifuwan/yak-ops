package io.yak.ops.business.datasource.domain.catalog;

/** Typed table discovery query. */
public record CatalogTableQuery(String database, String schema, String keyword, Integer limit) {

    public CatalogTableQuery(String database, String schema, String keyword) {
        this(database, schema, keyword, null);
    }

    public CatalogTableQuery {
        database = normalizeNullable(database);
        schema = normalizeNullable(schema);
        keyword = normalizeNullable(keyword);
        limit = limit == null ? null : Math.max(1, limit);
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
