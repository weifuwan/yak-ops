package io.yak.ops.core.data;

import java.util.Objects;

/**
 * Identifies a physical table without depending on a database vendor or product route.
 *
 * <p>Catalog and schema are optional. Names retain their original case and are never parsed
 * from a concatenated string, so quoted identifiers containing dots remain unambiguous.
 */
public record TableId(String catalog, String schema, String table) {

    public TableId {
        catalog = normalize(catalog);
        schema = normalize(schema);
        table = Objects.requireNonNull(table, "table").trim();
        if (table.isEmpty()) {
            throw new IllegalArgumentException("Table name must not be empty");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
