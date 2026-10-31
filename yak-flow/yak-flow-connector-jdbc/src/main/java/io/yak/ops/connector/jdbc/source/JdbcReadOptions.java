package io.yak.ops.connector.jdbc.source;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import java.util.Objects;

/** Immutable per-table read intent frozen into every JDBC split. */
public final class JdbcReadOptions {
    private final String where;
    private final String splitKey;
    private final int fetchSize;
    private final int queryTimeoutSeconds;

    public JdbcReadOptions(String where, String splitKey, int fetchSize, int queryTimeoutSeconds) {
        this.where = JdbcReadQuery.validateWhere(where);
        this.splitKey = splitKey == null ? null : splitKey.trim();
        if (this.splitKey != null && (this.splitKey.isBlank() || this.splitKey.indexOf('\u0000') >= 0)) {
            throw new IllegalArgumentException("JDBC split key must name one nonblank column");
        }
        if (fetchSize < 1 || fetchSize > 10_000 || queryTimeoutSeconds < 1 || queryTimeoutSeconds > 300) {
            throw new IllegalArgumentException("JDBC fetch size or query timeout is outside its supported range");
        }
        this.fetchSize = fetchSize;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public static JdbcReadOptions from(ReadonlyConfig options) {
        Objects.requireNonNull(options, "options");
        return new JdbcReadOptions(
                options.get(JdbcSourceOptions.READ_WHERE),
                options.get(JdbcSourceOptions.SPLIT_KEY),
                options.get(JdbcSourceOptions.RESULT_SET_FETCH_SIZE),
                options.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS));
    }

    public String where() {
        return where;
    }

    public String splitKey() {
        return splitKey;
    }

    public int fetchSize() {
        return fetchSize;
    }

    public int queryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JdbcReadOptions selected)) return false;
        return Objects.equals(where, selected.where)
                && Objects.equals(splitKey, selected.splitKey)
                && fetchSize == selected.fetchSize
                && queryTimeoutSeconds == selected.queryTimeoutSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(where, splitKey, fetchSize, queryTimeoutSeconds);
    }
}
