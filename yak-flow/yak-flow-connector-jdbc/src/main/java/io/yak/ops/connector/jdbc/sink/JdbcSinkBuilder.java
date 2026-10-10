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

/**
 * Collects one-table or multi-table JDBC Sink options without opening a database connection.
 *
 * <p>Single-table field setters and {@link #withTablePlans(List)} are mutually exclusive.
 * A JDBC URL selects a vendor dialect through SPI unless a custom provider supplies one;
 * all write routes are validated when {@link #build()} is called.
 */
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

    /**
     * Uses DriverManager connections and resolves the dialect from the supplied JDBC URL.
     *
     * <p>This replaces any previously configured custom connection provider.
     */
    public JdbcSinkBuilder withConnectionOptions(JdbcConnectionOptions options) {
        connectionOptions = Objects.requireNonNull(options, "options");
        connections = null;
        return this;
    }

    /**
     * Uses caller-supplied connections, such as an isolated driver or tunneled provider.
     *
     * <p>A custom provider requires an explicit {@link #withDialect(JdbcDialect)} selection
     * because no JDBC URL is available for automatic vendor discovery.
     */
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

    /**
     * Sets an immutable collection of resolved source-to-target write routes.
     *
     * <p>Do not combine this option with the single-table setters. Every source and target
     * table must occur exactly once, and each plan must describe matching logical types.
     *
     * @param plans nonempty collection of independent table routes
     * @return this builder
     */
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

    /**
     * Validates the selected connection strategy, dialect and table routes without opening I/O.
     *
     * @return a reusable Sink definition; each task attempt opens its own Writer connection
     * @throws IllegalArgumentException if multi-table and single-table settings conflict
     */
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
