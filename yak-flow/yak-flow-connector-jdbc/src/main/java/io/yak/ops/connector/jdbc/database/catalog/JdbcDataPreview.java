package io.yak.ops.connector.jdbc.database.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable bounded query results; intentionally does not expose row data through toString. */
public final class JdbcDataPreview {
    private final List<JdbcColumnInfo> columns;
    private final List<List<Object>> rows;
    private final boolean truncated;

    public JdbcDataPreview(List<JdbcColumnInfo> columns, List<List<Object>> rows, boolean truncated) {
        this.columns = List.copyOf(columns);
        this.rows = rows.stream()
                .map(row -> Collections.unmodifiableList(new ArrayList<>(row)))
                .toList();
        this.truncated = truncated;
    }

    public List<JdbcColumnInfo> columns() {
        return columns;
    }

    public List<List<Object>> rows() {
        return rows;
    }

    public boolean truncated() {
        return truncated;
    }
}
