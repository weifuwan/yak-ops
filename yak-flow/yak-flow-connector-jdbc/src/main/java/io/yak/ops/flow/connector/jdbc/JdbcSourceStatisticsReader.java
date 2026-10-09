package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialect;
import io.yak.ops.flow.connector.jdbc.dialect.JdbcDialects;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionRuntime;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.Optional;

/**
 * 读取 bounded JDBC Source 的 MIN / MAX / COUNT 分片统计。
 *
 * <p>只有单整数主键可返回统计；无可用分片列或空表时返回 Optional.empty()。</p>
 *
 * @author weifuwan
 * @since 2026-10-06
 */
public final class JdbcSourceStatisticsReader {

    private final JdbcConnectionProvider connectionProvider;

    public JdbcSourceStatisticsReader() {
        this(JdbcConnectionRuntime.getInstance());
    }

    public JdbcSourceStatisticsReader(JdbcConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    public Optional<JdbcSourceStatistics> read(
            DataSourceConnection connection, DataSourceTablePath table, YakTableSchema schema, int timeoutSeconds)
            throws Exception {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be greater than 0");
        }

        Optional<String> splitColumn = JdbcNumericSplitConfig.eligibleColumn(schema);
        if (splitColumn.isEmpty()) return Optional.empty();

        JdbcDialect dialect = JdbcDialects.forType(connection.type());
        try (Connection opened = connectionProvider.open(connection, timeoutSeconds)) {
            opened.setReadOnly(true);
            try (var statement = opened.prepareStatement(dialect.splitStatisticsSql(table, splitColumn.get()))) {
                statement.setQueryTimeout(timeoutSeconds);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("JDBC split statistics query returned no row");
                    }

                    long rowCount = resultSet.getLong(3);
                    if (rowCount <= 0) return Optional.empty();

                    long lowerBound = resultSet.getLong(1);
                    if (resultSet.wasNull()) return Optional.empty();
                    long upperBound = resultSet.getLong(2);
                    if (resultSet.wasNull()) return Optional.empty();

                    return Optional.of(new JdbcSourceStatistics(splitColumn.get(), lowerBound, upperBound, rowCount));
                }
            }
        }
    }
}
