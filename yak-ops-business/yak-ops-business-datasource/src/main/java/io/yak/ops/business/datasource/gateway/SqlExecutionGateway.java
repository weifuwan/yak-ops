package io.yak.ops.business.datasource.gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Business-side Port for physical SQL execution. Datasource Plugin SPI stays behind its Adapter. */
public interface SqlExecutionGateway {

    Session open(String dataSourceId);

    interface Session extends AutoCloseable {

        Result execute(Command command);

        default boolean supportsTransactions() {
            return false;
        }

        default void beginTransaction() {
            throw new UnsupportedOperationException("Transactions are not supported");
        }

        default void commitTransaction() {
            throw new UnsupportedOperationException("Transactions are not supported");
        }

        default void rollbackTransaction() {
            throw new UnsupportedOperationException("Transactions are not supported");
        }

        default void cancel() {}

        @Override
        default void close() {}
    }

    record Command(String sql, List<Object> parameters, int maxRows, int timeoutSeconds) {

        public Command {
            if (sql == null || sql.isBlank()) throw new IllegalArgumentException("sql must not be blank");
            sql = sql.trim();
            parameters = immutableNullableList(parameters);
            if (maxRows <= 0) throw new IllegalArgumentException("maxRows must be greater than zero");
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("timeoutSeconds must be greater than zero");
            }
        }
    }

    record Column(String name, String label, String typeName, int jdbcType, boolean nullable) {}

    record Result(
            boolean resultSet, List<Column> columns, List<List<Object>> rows, long affectedRows, boolean truncated) {

        public Result {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = immutableNullableRows(rows);
        }
    }

    private static List<Object> immutableNullableList(List<Object> values) {
        if (values == null || values.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<List<Object>> immutableNullableRows(List<List<Object>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<List<Object>> copied = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            copied.add(row == null ? List.of() : immutableNullableList(row));
        }
        return Collections.unmodifiableList(copied);
    }
}
