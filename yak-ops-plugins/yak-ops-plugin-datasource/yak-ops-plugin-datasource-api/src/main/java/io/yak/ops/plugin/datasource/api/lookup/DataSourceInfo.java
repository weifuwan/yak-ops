package io.yak.ops.plugin.datasource.api.lookup;

import java.util.Objects;

/** Non-secret identity and bound namespace of an authorized datasource. */
public record DataSourceInfo(String type, String database, String schema, long revision) {
    public DataSourceInfo {
        Objects.requireNonNull(type, "type");
        if (revision < 1) throw new IllegalArgumentException("Datasource revision is required");
    }

    public DataSourceTablePath resolveTable(String selectedDatabase, String selectedSchema, String table) {
        return new DataSourceTablePath(
                namespace(database, selectedDatabase), namespace(schema, selectedSchema), table);
    }

    private static String namespace(String bound, String selected) {
        if (bound != null && !bound.isBlank() && selected != null && !selected.isBlank() && !bound.equals(selected)) {
            throw new IllegalArgumentException("Selected namespace differs from datasource binding");
        }
        return selected == null || selected.isBlank() ? bound : selected;
    }
}
