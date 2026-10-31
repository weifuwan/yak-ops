package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.configuration.ReadonlyConfig;
import java.util.List;

/** Immutable per-table batch limits and ordered SQL surrounding a bounded data execution. */
public final class JdbcWriteOptions {
    private final int batchSize;
    private final List<String> preSql;
    private final List<String> postSql;

    public JdbcWriteOptions(int batchSize, List<String> preSql, List<String> postSql) {
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("JDBC batch size must be between 1 and 10000");
        }
        this.batchSize = batchSize;
        this.preSql = JdbcSqlHooks.validateStatements(preSql);
        this.postSql = JdbcSqlHooks.validateStatements(postSql);
    }

    public static JdbcWriteOptions from(ReadonlyConfig options) {
        return new JdbcWriteOptions(
                options.get(JdbcSinkOptions.BATCH_SIZE),
                JdbcSqlHooks.parseStatements(options.get(JdbcSinkOptions.PRE_SQL)),
                JdbcSqlHooks.parseStatements(options.get(JdbcSinkOptions.POST_SQL)));
    }

    public int batchSize() {
        return batchSize;
    }

    public List<String> preSql() {
        return preSql;
    }

    public List<String> postSql() {
        return postSql;
    }

    public boolean hasSqlHooks() {
        return !preSql.isEmpty() || !postSql.isEmpty();
    }
}
