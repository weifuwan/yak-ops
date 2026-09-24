package io.yak.ops.spi.datasource.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** JDBC-neutral request used by SQL-capable datasource plugins. */
public record DataSourceSqlRequest(String sql, int maxRows, int timeoutSeconds, List<Object> parameters) {

    public DataSourceSqlRequest {
        sql = Objects.requireNonNull(sql, "sql").trim();
        if (sql.isEmpty()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be greater than zero");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be greater than zero");
        }
        parameters = immutableNullableList(parameters);
    }

    public DataSourceSqlRequest(String sql, int maxRows, int timeoutSeconds) {
        this(sql, maxRows, timeoutSeconds, List.of());
    }

    private static List<Object> immutableNullableList(List<Object> values) {
        if (values == null || values.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
