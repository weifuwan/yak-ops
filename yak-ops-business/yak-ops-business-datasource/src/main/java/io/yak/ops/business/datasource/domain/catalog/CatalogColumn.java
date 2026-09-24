package io.yak.ops.business.datasource.domain.catalog;

/** Business-owned column metadata. */
public record CatalogColumn(
        String name,
        String typeName,
        int jdbcType,
        Integer size,
        Integer scale,
        boolean nullable,
        int ordinalPosition,
        boolean primaryKey,
        String remarks) {

    public CatalogColumn {
        name = requireText(name, "Catalog 字段名不能为空");
        typeName = normalizeNullable(typeName);
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
