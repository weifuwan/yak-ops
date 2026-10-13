package io.yak.ops.plugin.datasource.api.lookup;

import io.yak.ops.core.data.TableId;

/** Non-secret identity and namespace of an authorized datasource. */
public record DataSourceInfo(String type, String database, String schema) {
    /**
     * Resolves a selected table within this datasource's bound namespace.
     *
     * @param selectedDatabase selected catalog or null to use the bound catalog
     * @param selectedSchema selected schema or null to use the bound schema
     * @param table selected table name
     * @return the canonical table identifier
     */
    public TableId resolveTable(String selectedDatabase, String selectedSchema, String table) {
        return new TableId(namespace(database, selectedDatabase), namespace(schema, selectedSchema), table);
    }

    private static String namespace(String bound, String selected) {
        if (bound != null && !bound.isBlank() && selected != null && !selected.isBlank() && !bound.equals(selected)) {
            throw new IllegalArgumentException("Selected namespace differs from datasource binding");
        }
        if (selected == null || selected.isBlank()) {
            return bound;
        }
        return selected;
    }
}
