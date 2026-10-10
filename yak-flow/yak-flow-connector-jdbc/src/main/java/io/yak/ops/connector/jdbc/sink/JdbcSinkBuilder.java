package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.connection.DriverManagerJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Builds a single-table or multi-table JDBC Sink without product-layer dependencies. */
public final class JdbcSinkBuilder {

    private JdbcConnectionOptions connectionOptions;
    private JdbcConnectionProvider connections;
    private JdbcDialect dialect;
    private TableId sourceTable;
    private TableId targetTable;
    private TableSchema schema;
    private JdbcWriteMode mode = JdbcWriteMode.APPEND;
    private List<JdbcTableWritePlan> tablePlans;
    private BatchFlushPolicy batchPolicy = new BatchFlushPolicy(500, Duration.ZERO);

    public JdbcSinkBuilder withConnectionOptions(JdbcConnectionOptions options) {
        connectionOptions = Objects.requireNonNull(options, "options");
        connections = null;
        return this;
    }

    public JdbcSinkBuilder withConnectionProvider(JdbcConnectionProvider provider) {
        connections = Objects.requireNonNull(provider, "provider");
        connectionOptions = null;
        return this;
    }

    /** Explicit vendor selection for custom connection providers or test databases. */
    public JdbcSinkBuilder withDialect(JdbcDialect jdbcDialect) {
        dialect = Objects.requireNonNull(jdbcDialect, "jdbcDialect");
        return this;
    }

    public JdbcSinkBuilder withSourceTable(TableId table) {
        sourceTable = Objects.requireNonNull(table, "table");
        return this;
    }

    public JdbcSinkBuilder withTargetTable(TableId table) {
        targetTable = Objects.requireNonNull(table, "table");
        return this;
    }

    public JdbcSinkBuilder withSchema(TableSchema tableSchema) {
        schema = Objects.requireNonNull(tableSchema, "tableSchema");
        return this;
    }

    public JdbcSinkBuilder withWriteMode(JdbcWriteMode writeMode) {
        mode = Objects.requireNonNull(writeMode, "writeMode");
        return this;
    }

    /** Replaces the single-table configuration with resolved source-to-target routes. */
    public JdbcSinkBuilder withTablePlans(List<JdbcTableWritePlan> plans) {
        tablePlans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        if (tablePlans.isEmpty()) {
            throw new IllegalArgumentException("JDBC Sink table routes must not be empty");
        }
        return this;
    }

    public JdbcSinkBuilder withBatchFlushPolicy(BatchFlushPolicy policy) {
        batchPolicy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    public JdbcSink build() {
        JdbcConnectionProvider provider = connections;
        if (provider == null) {
            JdbcConnectionOptions options = Objects.requireNonNull(connectionOptions, "connectionOptions");
            provider = new DriverManagerJdbcConnectionProvider(options);
        }
        JdbcDialect resolvedDialect = dialect;
        if (resolvedDialect == null) {
            if (connectionOptions == null) {
                throw new IllegalArgumentException("JDBC dialect is required for a custom connection provider");
            }
            resolvedDialect = JdbcFactoryLoader.loadDialect(connectionOptions.url());
        }
        if (tablePlans != null) {
            if (sourceTable != null || targetTable != null || schema != null || mode != JdbcWriteMode.APPEND) {
                throw new IllegalArgumentException("Single-table and multi-table JDBC Sink settings cannot be mixed");
            }
            return new JdbcSink(provider, resolvedDialect, tablePlans, batchPolicy);
        }
        TableId target = Objects.requireNonNull(targetTable, "targetTable");
        JdbcTableWritePlan plan = new JdbcTableWritePlan(
                sourceTable == null ? target : sourceTable, target, Objects.requireNonNull(schema, "schema"), mode);
        return new JdbcSink(provider, resolvedDialect, plan, batchPolicy);
    }
}
