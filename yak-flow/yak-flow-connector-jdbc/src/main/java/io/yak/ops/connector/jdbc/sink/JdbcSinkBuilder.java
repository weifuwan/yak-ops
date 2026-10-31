package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Collects one-table or multi-table JDBC Sink options without opening a database connection.
 *
 * <p>Single-table field setters and {@link #withTablePlans(List)} are mutually exclusive.
 * A JDBC URL selects a vendor dialect through SPI unless a custom provider supplies one;
 * all write routes are validated when {@link #build()} is called.
 */
public final class JdbcSinkBuilder {
    private Map<TableId, JdbcWriteOptions> tableWriteOptions = Map.of();

    private io.yak.ops.connector.base.metrics.TableMetrics metrics =
            new io.yak.ops.connector.base.metrics.TableMetrics();
    private JdbcConnectionOptions connectionOptions;
    private JdbcConnectionProvider connections;
    private JdbcDialect dialect;
    private TableId sourceTable;
    private TableId targetTable;
    private TableSchema schema;
    private JdbcWriteMode mode = JdbcWriteMode.APPEND;
    private JdbcBatchOrder batchOrder = JdbcBatchOrder.INPUT_ORDER;
    private int statementCacheSize = JdbcSinkOptions.STATEMENT_CACHE_SIZE.defaultValue();
    private List<JdbcTableWritePlan> tablePlans;
    private BatchFlushPolicy batchPolicy = new BatchFlushPolicy(500, Duration.ZERO);
    private Supplier<? extends MetadataApplier> metadataApplierFactory;

    /**
     * Uses DriverManager connections and resolves the dialect from the supplied JDBC URL.
     *
     * <p>These connection options select DriverManager and clear any configured custom
     * connection provider.
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

    /** Attaches the attempt-local measurement collector without affecting write or checkpoint behavior. */
    public JdbcSinkBuilder withTableMetrics(io.yak.ops.connector.base.metrics.TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        return this;
    }

    /** Sets route-owned SQL hooks and maximum batch sizes without changing connection ownership. */
    public JdbcSinkBuilder withTableWriteOptions(Map<TableId, JdbcWriteOptions> options) {
        tableWriteOptions = Map.copyOf(options);
        return this;
    }

    /**
     * Selects physical ordering; independent-table grouping requires an explicit caller decision.
     *
     * @param order whole-input order or independent destination groups
     * @return this builder
     */
    public JdbcSinkBuilder withBatchOrder(JdbcBatchOrder order) {
        batchOrder = Objects.requireNonNull(order, "order");
        return this;
    }

    /** Limits live prepared statements for each Writer, not each individual table. */
    public JdbcSinkBuilder withStatementCacheSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("JDBC statement cache size must be positive");
        }
        statementCacheSize = size;
        return this;
    }

    /** Supplies the per-job metadata applier used only when runtime schema evolution is enabled. */
    public JdbcSinkBuilder withMetadataApplierFactory(Supplier<? extends MetadataApplier> factory) {
        metadataApplierFactory = Objects.requireNonNull(factory, "factory");
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
            provider = new DefaultJdbcConnectionProvider(options);
        }
        JdbcDialect resolvedDialect = dialect;
        if (resolvedDialect == null) {
            if (connectionOptions == null) {
                throw new IllegalArgumentException("JDBC dialect is required for a custom connection provider");
            }
            resolvedDialect = JdbcFactoryLoader.loadDialect(connectionOptions.url());
        }
        Supplier<? extends MetadataApplier> applierFactory = metadataApplierFactory;
        if (applierFactory == null && connectionOptions != null) {
            String url = connectionOptions.url();
            JdbcConnectionProvider applierConnections = provider;
            JdbcDialect applierDialect = resolvedDialect;
            applierFactory = () -> new JdbcMetadataApplier(
                    applierConnections,
                    applierDialect,
                    JdbcFactoryLoader.loadCatalog(url, applierConnections),
                    JdbcMetadataApplier.DEFAULT_STATEMENT_TIMEOUT_SECONDS);
        }
        if (tablePlans != null) {
            if (sourceTable != null || targetTable != null || schema != null || mode != JdbcWriteMode.APPEND) {
                throw new IllegalArgumentException("Single-table and multi-table JDBC Sink settings cannot be mixed");
            }
            return new JdbcSink(
                    provider,
                    resolvedDialect,
                    tablePlans,
                    batchPolicy,
                    metrics,
                    batchOrder,
                    statementCacheSize,
                    applierFactory,
                    tableWriteOptions);
        }
        TableId target = Objects.requireNonNull(targetTable, "targetTable");
        JdbcTableWritePlan plan = new JdbcTableWritePlan(
                sourceTable == null ? target : sourceTable, target, Objects.requireNonNull(schema, "schema"), mode);
        return new JdbcSink(
                provider,
                resolvedDialect,
                List.of(plan),
                batchPolicy,
                metrics,
                batchOrder,
                statementCacheSize,
                applierFactory,
                tableWriteOptions);
    }
}
