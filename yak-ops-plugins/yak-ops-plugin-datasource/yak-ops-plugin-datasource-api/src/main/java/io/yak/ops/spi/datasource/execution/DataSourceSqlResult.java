package io.yak.ops.spi.datasource.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JDBC-neutral SQL execution result. */
public record DataSourceSqlResult(
        boolean resultSet,
        List<DataSourceSqlColumn> columns,
        List<List<Object>> rows,
        long affectedRows,
        boolean truncated) {

    public DataSourceSqlResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = immutableRows(rows);
    }

    public static DataSourceSqlResult resultSet(
            List<DataSourceSqlColumn> columns, List<List<Object>> rows, boolean truncated) {
        return new DataSourceSqlResult(true, columns, rows, 0L, truncated);
    }

    public static DataSourceSqlResult updateCount(long affectedRows) {
        return new DataSourceSqlResult(false, List.of(), List.of(), affectedRows, false);
    }

    private static List<List<Object>> immutableRows(List<List<Object>> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<List<Object>> copied = new ArrayList<>(values.size());
        for (List<Object> row : values) {
            List<Object> cells = row == null ? new ArrayList<>() : new ArrayList<>(row);
            copied.add(Collections.unmodifiableList(cells));
        }
        return Collections.unmodifiableList(copied);
    }
}
