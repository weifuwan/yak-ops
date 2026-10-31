package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.schema.JdbcSchemaDerivator;
import io.yak.ops.connector.jdbc.database.schema.SchemaChangePlan;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.api.connector.sink.SchemaEvolutionSink;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.event.AddColumnEvent;
import io.yak.ops.core.event.AlterColumnTypeEvent;
import io.yak.ops.core.event.DropColumnEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeRequest;
import io.yak.ops.core.event.SourceSchemaChange;
import io.yak.ops.core.schema.PhysicalColumn;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.schema.SchemaVersionedTableState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Defines one or more JDBC source-to-target table routes for a reusable Sink.
 *
 * <p>Each execution attempt creates its own {@link JdbcWriter} with a distinct JDBC connection
 * and transaction. APPEND accepts only inserts; native UPSERT applies key-aware changelog
 * records. Split UPDATE_BEFORE/UPDATE_AFTER pairs must be adjacent on the same subtask.
 *
 * <p>Flushes are synchronous and at-least-once; there is no XA committer, exactly-once
 * guarantee, or global ordering across parallel Sink subtasks.
 */
public final class JdbcSink implements Sink<TableRecord>, SchemaEvolutionSink {

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private volatile List<JdbcTableWritePlan> plans;
    private final BatchFlushPolicy batchPolicy;
    private final TableMetrics metrics;
    private final JdbcBatchOrder batchOrder;
    private final int statementCacheSize;
    private final Supplier<? extends MetadataApplier> metadataApplierFactory;
    private final Map<TableId, JdbcWriteOptions> tableWriteOptions;

    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            JdbcTableWritePlan plan,
            BatchFlushPolicy batchPolicy) {
        this(connections, dialect, List.of(plan), batchPolicy);
    }

    /**
     * Validates a multi-table Sink definition before opening any JDBC connection.
     *
     * <p>Source and target tables must be unique. Native UPSERT and DELETE SQL support
     * are validated through the dialect at construction time.
     *
     * @param connections capability that opens independent connections for each Writer
     * @param dialect reusable vendor-specific SQL and row-conversion rules
     * @param plans nonempty source-to-target table routes
     * @param batchPolicy size and optional mailbox processing-time flush policy
     */
    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy) {
        this(connections, dialect, plans, batchPolicy, new TableMetrics());
    }

    /** Creates routes whose measurements count only acknowledged transaction commits. */
    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy,
            TableMetrics metrics) {
        this(
                connections,
                dialect,
                plans,
                batchPolicy,
                metrics,
                JdbcBatchOrder.INPUT_ORDER,
                JdbcSinkOptions.STATEMENT_CACHE_SIZE.defaultValue());
    }

    /** Creates an explicitly ordered Sink without inferring table independence from UPSERT mode. */
    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize) {
        this(connections, dialect, plans, batchPolicy, metrics, batchOrder, statementCacheSize, null);
    }

    /** Creates a reusable Sink with a factory for one job-attempt-owned MetadataApplier. */
    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize,
            Supplier<? extends MetadataApplier> metadataApplierFactory) {
        this(
                connections,
                dialect,
                plans,
                batchPolicy,
                metrics,
                batchOrder,
                statementCacheSize,
                metadataApplierFactory,
                Map.of());
    }

    public JdbcSink(
            JdbcConnectionProvider connections,
            JdbcDialect dialect,
            List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy,
            TableMetrics metrics,
            JdbcBatchOrder batchOrder,
            int statementCacheSize,
            Supplier<? extends MetadataApplier> metadataApplierFactory,
            Map<TableId, JdbcWriteOptions> tableWriteOptions) {
        this.tableWriteOptions = Map.copyOf(tableWriteOptions);
        this.batchOrder = Objects.requireNonNull(batchOrder, "batchOrder");
        if (statementCacheSize < 1) {
            throw new IllegalArgumentException("JDBC statement cache size must be positive");
        }
        this.statementCacheSize = statementCacheSize;
        this.metadataApplierFactory = metadataApplierFactory;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        this.batchPolicy = Objects.requireNonNull(batchPolicy, "batchPolicy");
        if (this.plans.isEmpty()) {
            throw new IllegalArgumentException("JDBC Sink needs at least one table route");
        }
        Set<TableId> sources = new HashSet<>();
        Set<TableId> targets = new HashSet<>();
        for (JdbcTableWritePlan plan : this.plans) {
            if (!sources.add(plan.sourceTable()) || !targets.add(plan.targetTable())) {
                throw new IllegalArgumentException("JDBC Sink source and target table routes must be unique");
            }
            plan.sql(dialect);
            if (plan.writeMode() == JdbcWriteMode.UPSERT) {
                dialect.deleteSql(plan.targetTable(), plan.schema());
            } else if (plan.writeMode() == JdbcWriteMode.RETRACT) {
                dialect.exactRowDeleteSql(plan.targetTable(), plan.schema());
            }
        }
        if (!sources.containsAll(this.tableWriteOptions.keySet())) {
            throw new IllegalArgumentException("JDBC write settings reference an unconfigured source route");
        }
        // Validate hook table scope before any SQL or writer is opened.
        batchExecutionHooks();
    }

    @Override
    public List<BatchExecutionHook> batchExecutionHooks() {
        List<BatchExecutionHook> hooks = new ArrayList<>();
        for (JdbcTableWritePlan plan : plans) {
            JdbcWriteOptions options = tableWriteOptions.get(plan.sourceTable());
            if (options == null) continue;
            for (String sql : options.preSql()) {
                hooks.add(JdbcSqlHooks.operation(
                        connections, plan.targetTable(), BatchExecutionHook.Phase.BEFORE_DATA, sql));
            }
            for (String sql : options.postSql()) {
                hooks.add(JdbcSqlHooks.operation(
                        connections, plan.targetTable(), BatchExecutionHook.Phase.AFTER_DATA, sql));
            }
        }
        return List.copyOf(hooks);
    }

    /** Starts configuring a single-table or multi-table JDBC Sink. */
    public static JdbcSinkBuilder builder() {
        return new JdbcSinkBuilder();
    }

    /**
     * Creates an independent task-local Writer and releases its resources if initialization fails.
     *
     * <p>The Runtime provides the mailbox timer and the actual Sink subtask identity.
     * This definition never caches the returned Writer across execution attempts.
     */
    @Override
    public MetadataApplier createMetadataApplier() {
        if (metadataApplierFactory == null) {
            throw new UnsupportedOperationException("This JDBC Sink was built without a schema applier factory");
        }
        return Objects.requireNonNull(metadataApplierFactory.get(), "MetadataApplier factory returned null");
    }

    /**
     * Uses the existing JDBC SchemaDerivator and dialect rather than interpreting raw MySQL DDL.
     * Only identity-mapped, previously configured table routes are changed automatically.
     */
    @Override
    public synchronized SchemaChangeRequest planSchemaChange(SourceSchemaChange change) {
        return planSchemaChange(change, false);
    }

    @Override
    public synchronized SchemaChangeRequest planSchemaChange(SourceSchemaChange change, boolean allowDropColumns) {
        Objects.requireNonNull(change, "change");
        JdbcTableWritePlan plan = plans.stream()
                .filter(route -> route.sourceTable().equals(change.tableId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CDC DDL references an unconfigured table"));
        if (plan.writeMode() == JdbcWriteMode.RETRACT) {
            throw new UnsupportedOperationException("Keyless RETRACT schema changes require coordinated acceptance");
        }

        Schema currentSource = Schema.from(plan.sourceSchema(), null, java.util.Map.of(), java.util.Map.of());
        if (!currentSource.toTableSchema().equals(change.before().toTableSchema())) {
            throw new IllegalStateException("MySQL CDC DDL is stale relative to the configured write route");
        }
        for (int index = 0; index < plan.schema().columnCount(); index++) {
            String source = plan.sourceSchema()
                    .column(plan.sourcePositions().get(index))
                    .name();
            if (!plan.schema().column(index).name().equals(source)) {
                throw new UnsupportedOperationException(
                        "Automatic CDC DDL requires identity-mapped source and target columns");
            }
        }

        Schema currentTarget = Schema.from(plan.schema(), null, java.util.Map.of(), java.util.Map.of());
        Set<String> nextNames = new HashSet<>();
        change.after().columns().forEach(column -> nextNames.add(column.name()));
        Set<String> deletedBySource = new HashSet<>();
        if (allowDropColumns) {
            for (PhysicalColumn previous : change.before().columns()) {
                if (!nextNames.contains(previous.name())) {
                    if (change.before().primaryKeys().contains(previous.name())
                            || plan.schema().columns().stream()
                                    .noneMatch(c -> c.name().equals(previous.name()))) {
                        throw new UnsupportedOperationException(
                                "CDC DROP COLUMN does not name a managed non-key target column");
                    }
                    deletedBySource.add(previous.name());
                }
            }
        }
        SchemaChangePlan diff = JdbcSchemaDerivator.derive(
                plan.targetTable(), change.after(), Optional.of(currentTarget), dialect, deletedBySource);
        if (diff.blocked()) {
            throw new UnsupportedOperationException("MySQL CDC schema change is incompatible with the target: "
                    + diff.differences().stream()
                            .filter(d -> d.kind() == SchemaChangePlan.Kind.INCOMPATIBLE)
                            .map(SchemaChangePlan.Difference::reason)
                            .distinct()
                            .toList());
        }
        List<PhysicalColumn> columns = new ArrayList<>(currentTarget.columns());
        for (SchemaChangeEvent event : diff.changes()) {
            if (event instanceof AddColumnEvent add) {
                for (AddColumnEvent.ColumnWithPosition field : add.addedColumns()) {
                    if (field.position() != AddColumnEvent.ColumnPosition.LAST) {
                        throw new UnsupportedOperationException("Only append-column CDC DDL is supported");
                    }
                    columns.add(field.column());
                }
            } else if (event instanceof AlterColumnTypeEvent alter) {
                for (int index = 0; index < columns.size(); index++) {
                    PhysicalColumn field = columns.get(index);
                    if (alter.typeMapping().containsKey(field.name())) {
                        columns.set(index, field.copy(alter.typeMapping().get(field.name())));
                    }
                }
            } else if (event instanceof DropColumnEvent drop) {
                columns.removeIf(column -> drop.droppedColumnNames().contains(column.name()));
            } else {
                throw new UnsupportedOperationException("Unsupported runtime target schema event");
            }
        }
        Schema nextTarget = currentTarget.withColumns(columns);
        return new SchemaChangeRequest(
                change.eventId(),
                change.tableId(),
                plan.targetTable(),
                change.before(),
                change.after(),
                currentTarget,
                nextTarget,
                diff.changes());
    }

    @Override
    public synchronized void schemaChangeCompleted(SchemaChangeRequest request) {
        List<JdbcTableWritePlan> changed = new ArrayList<>(plans.size());
        int matches = 0;
        for (JdbcTableWritePlan plan : plans) {
            if (plan.sourceTable().equals(request.sourceTable())
                    && plan.targetTable().equals(request.targetTable())) {
                changed.add(plan.withSchemas(
                        request.sourceSchemaAfter().toTableSchema(),
                        request.targetSchemaAfter().toTableSchema()));
                matches++;
            } else {
                changed.add(plan);
            }
        }
        if (matches != 1) {
            throw new IllegalStateException("Committed schema event has no matching JDBC route");
        }
        plans = List.copyOf(changed);
    }

    @Override
    public synchronized void restoreSchemaState(List<SchemaVersionedTableState> states) {
        Objects.requireNonNull(states, "states");
        List<JdbcTableWritePlan> restored = new java.util.ArrayList<>(plans);
        Set<TableId> seen = new HashSet<>();
        for (SchemaVersionedTableState state : states) {
            if (!seen.add(state.targetTable())) {
                throw new IllegalArgumentException("Duplicate restored JDBC target schema state");
            }
            int matched = -1;
            for (int index = 0; index < restored.size(); index++) {
                JdbcTableWritePlan plan = restored.get(index);
                if (plan.sourceTable().equals(state.sourceTable())
                        && plan.targetTable().equals(state.targetTable())) {
                    matched = index;
                    break;
                }
            }
            if (matched < 0) {
                throw new IllegalStateException("Checkpoint schema state does not match a configured JDBC route");
            }
            JdbcTableWritePlan plan = restored.get(matched);
            restored.set(
                    matched,
                    plan.withSchemas(
                            state.sourceSchema().toTableSchema(),
                            state.targetSchema().toTableSchema()));
        }
        plans = List.copyOf(restored);
    }

    @Override
    public JdbcWriter createWriter(WriterInitContext context) throws Exception {
        Objects.requireNonNull(context, "context");
        JdbcOutputFormat output = new JdbcOutputFormat(
                connections,
                dialect,
                plans,
                metrics,
                batchOrder,
                statementCacheSize,
                tableWriteOptions.entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, entry -> entry.getValue().batchSize())));
        try {
            return new JdbcWriter(output, batchPolicy, context);
        } catch (RuntimeException | Error failure) {
            try {
                output.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
