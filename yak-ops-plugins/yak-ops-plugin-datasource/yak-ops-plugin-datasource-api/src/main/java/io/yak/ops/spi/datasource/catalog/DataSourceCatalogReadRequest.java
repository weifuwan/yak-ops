package io.yak.ops.spi.datasource.catalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Typed Catalog read request used by datasource plugins. */
public record DataSourceCatalogReadRequest(Mode mode, String tablePath, String query, Map<String, String> variables) {

    public DataSourceCatalogReadRequest {
        mode = Objects.requireNonNull(mode, "mode");
        tablePath = trimToNull(tablePath);
        query = trimToNull(query);
        variables = variables == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(variables));
        if (mode == Mode.TABLE && tablePath == null) {
            throw new IllegalArgumentException("tablePath must not be blank in TABLE mode");
        }
        if (mode == Mode.SQL && query == null) {
            throw new IllegalArgumentException("query must not be blank in SQL mode");
        }
    }

    public static DataSourceCatalogReadRequest table(String tablePath, Map<String, String> variables) {
        return new DataSourceCatalogReadRequest(Mode.TABLE, tablePath, null, variables);
    }

    public static DataSourceCatalogReadRequest sql(String query, Map<String, String> variables) {
        return new DataSourceCatalogReadRequest(Mode.SQL, null, query, variables);
    }

    public boolean sqlMode() {
        return mode == Mode.SQL;
    }

    public enum Mode {
        TABLE,
        SQL
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
