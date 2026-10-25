package io.yak.ops.connector.jdbc.internal.executor;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.RowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Selects vendor statements for multi-table changelog records while preserving input order.
 *
 * <p>The buffered executor is the sole owner of detached records. This executor tracks only
 * a pending table identity during row admission and a temporary before-image reference during
 * synchronous flush. Consecutive same-statement operations use one driver batch; changing the
 * statement executes the preceding batch on the same JDBC transaction.
 *
 * <p>Matching-key UPDATE_BEFORE/UPDATE_AFTER pairs issue one UPSERT, whereas key changes
 * issue DELETE(old) then UPSERT(new). A partial or interleaved pair fails instead of
 * committing an unsafe half-update.
 */
public final class TableChangelogStatementExecutor implements JdbcBatchStatementExecutor<TableRecord> {

    private final Map<TableId, JdbcTableWritePlan> routes = new LinkedHashMap<>();
    private final Map<TableId, TableSimpleStatementExecutor> writes = new LinkedHashMap<>();
    private final Map<TableId, TableSimpleStatementExecutor> deletes = new LinkedHashMap<>();
    private final int statementCacheSize;
    private final boolean useReplayReceipts;
    private final JdbcDialect dialect;
    private final Set<TableSimpleStatementExecutor> prepared = new LinkedHashSet<>();
    private Connection connection;
    private JdbcReplayReceipts receipts;
    private TableSimpleStatementExecutor active;
    private TableId awaitingUpdateAfter;
    private ChangeEventId awaitingUpdateEvent;
    private TableRecord beforeInExecution;

    public TableChangelogStatementExecutor(JdbcDialect dialect, List<JdbcTableWritePlan> plans) {
        this(dialect, plans, 64);
    }

    /** Bounds live statements across all routes; SQL and converters remain immutable route definitions. */
    public TableChangelogStatementExecutor(
            JdbcDialect dialect, List<JdbcTableWritePlan> plans, int statementCacheSize) {
        this(dialect, plans, statementCacheSize, true);
    }

    /**
     * Prepared XA transactions must not also reserve durable Receipt rows.
     * Disabling receipts is allowed only for a checkpoint-coordinated XA owner.
     */
    public TableChangelogStatementExecutor(
            JdbcDialect dialect, List<JdbcTableWritePlan> plans, int statementCacheSize, boolean useReplayReceipts) {
        this.useReplayReceipts = useReplayReceipts;
        if (statementCacheSize < 1) {
            throw new IllegalArgumentException("JDBC statement cache size must be positive");
        }
        this.statementCacheSize = statementCacheSize;
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        installPlans(Objects.requireNonNull(dialect, "dialect"), Objects.requireNonNull(plans, "plans"));
    }

    /** Replaces route SQL and converters only after the owning Writer has flushed all records. */
    public void updatePlans(JdbcDialect dialect, List<JdbcTableWritePlan> plans) throws SQLException {
        if (connection == null) {
            throw new IllegalStateException("JDBC executor has no active connection");
        }
        if (active != null || awaitingUpdateAfter != null || awaitingUpdateEvent != null || beforeInExecution != null) {
            throw new IllegalStateException("Cannot replace JDBC routes while a batch or update pair is active");
        }
        closePreparedStatements();
        routes.clear();
        writes.clear();
        deletes.clear();
        installPlans(Objects.requireNonNull(dialect, "dialect"), Objects.requireNonNull(plans, "plans"));
    }

    private void installPlans(JdbcDialect dialect, List<JdbcTableWritePlan> plans) {
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("JDBC Sink requires at least one table route");
        }
        for (JdbcTableWritePlan plan : plans) {
            Objects.requireNonNull(plan, "plan");
            if (routes.putIfAbsent(plan.sourceTable(), plan) != null) {
                throw new IllegalArgumentException("Duplicate JDBC Sink source table route");
            }
            writes.put(
                    plan.sourceTable(),
                    new TableSimpleStatementExecutor(plan.sql(dialect), dialect.createRowConverter(plan.schema())));
            if (plan.writeMode() == JdbcWriteMode.UPSERT) {
                deletes.put(
                        plan.sourceTable(),
                        new TableSimpleStatementExecutor(
                                dialect.deleteSql(plan.targetTable(), plan.schema()),
                                dialect.createRowConverter(plan.keySchema())));
            } else if (plan.writeMode() == JdbcWriteMode.RETRACT) {
                deletes.put(
                        plan.sourceTable(),
                        new TableSimpleStatementExecutor(
                                dialect.exactRowDeleteSql(plan.targetTable(), plan.schema()),
                                dialect.createRowConverter(plan.schema()),
                                true));
            }
        }
    }

    /**
     * Validates adjacent UPDATE_BEFORE / UPDATE_AFTER events before entering the sole row buffer.
     *
     * <p>Each YakFlow {@code TableRecord} carries one {@code RowKind}. UPDATE_BEFORE and
     * UPDATE_AFTER must arrive consecutively on the same Sink subtask; malformed or
     * interleaved pairs fail closed.
     *
     * @param record source table ID, mutation kind and source-ordered row values
     * @return a validated, detached target-ordered row for the sole batch buffer
     * @throws IllegalArgumentException if the route is unknown or an UPDATE pair is malformed
     */
    public TableRecord snapshot(TableRecord record) {
        Objects.requireNonNull(record, "record");
        JdbcTableWritePlan plan = routes.get(record.tableId());
        if (plan == null) {
            throw new IllegalArgumentException("JDBC Sink received an unknown source table");
        }
        if (awaitingUpdateAfter != null
                && (record.rowKind() != RowKind.UPDATE_AFTER || !awaitingUpdateAfter.equals(record.tableId()))) {
            throw new IllegalArgumentException("UPDATE_BEFORE must be followed by UPDATE_AFTER for the same table");
        }
        if (plan.writeMode() == JdbcWriteMode.RETRACT && record.rowKind() == RowKind.UPDATE_AFTER) {
            if (awaitingUpdateAfter == null) {
                throw new IllegalArgumentException("JDBC RETRACT UPDATE_AFTER requires a preceding before-image");
            }
        }
        if (plan.writeMode() == JdbcWriteMode.RETRACT) {
            ChangeEventId identity = Objects.requireNonNull(
                    record.changeEventId(), "Keyless RETRACT requires a replay-stable CDC event ID");
            boolean update = record.rowKind() == RowKind.UPDATE_BEFORE || record.rowKind() == RowKind.UPDATE_AFTER;
            if (identity.imageCount() != (update ? 2 : 1)
                    || identity.imageIndex() != (record.rowKind() == RowKind.UPDATE_AFTER ? 1 : 0)) {
                throw new IllegalArgumentException("Keyless RETRACT requires complete CDC event image coordinates");
            }
        }
        if (awaitingUpdateAfter != null && plan.writeMode() == JdbcWriteMode.RETRACT) {
            ChangeEventId next = record.changeEventId();
            if ((awaitingUpdateEvent == null) != (next == null)
                    || (next != null
                            && (!awaitingUpdateEvent.sameEvent(next)
                                    || awaitingUpdateEvent.imageIndex() != 0
                                    || next.imageIndex() != 1
                                    || awaitingUpdateEvent.imageCount() != 2
                                    || next.imageCount() != 2))) {
                throw new IllegalArgumentException("JDBC RETRACT UPDATE images must share one complete CDC event");
            }
        }
        RowData projected = plan.project(record.row(), record.rowKind());
        TableRecord detached = new TableRecord(record.tableId(), record.rowKind(), projected, record.changeEventId());
        if (record.rowKind() == RowKind.UPDATE_BEFORE) {
            awaitingUpdateAfter = record.tableId();
            awaitingUpdateEvent = record.changeEventId();
        } else if (awaitingUpdateAfter != null) {
            awaitingUpdateAfter = null;
            awaitingUpdateEvent = null;
        }
        return detached;
    }

    /** Allows automatic flush only after all admitted UPDATE_BEFORE records have after-images. */
    public boolean canAutomaticallyFlush() {
        return awaitingUpdateAfter == null;
    }

    /**
     * Rejects checkpoint/end-of-input commits when an UPDATE_BEFORE has no after-image.
     *
     * @throws IllegalStateException if the admitted input ends in a partial UPDATE pair
     */
    public void requireCompleteUpdate() {
        if (awaitingUpdateAfter != null) {
            throw new IllegalStateException("Cannot checkpoint or finish an incomplete JDBC UPDATE pair");
        }
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        if (this.connection != null) {
            throw new IllegalStateException("JDBC executor already owns a connection");
        }
        this.connection = Objects.requireNonNull(connection, "connection");
        if (useReplayReceipts) {
            receipts = new JdbcReplayReceipts(connection, dialect, List.copyOf(routes.values()));
        }
    }

    /** Called only after switching away from and executing the previous active batch. */
    private void prepare(TableSimpleStatementExecutor statement) throws SQLException {
        if (connection == null) {
            throw new IllegalStateException("JDBC executor has no connection");
        }
        if (prepared.remove(statement)) {
            prepared.add(statement);
            return;
        }
        if (prepared.size() == statementCacheSize) {
            TableSimpleStatementExecutor oldest = prepared.iterator().next();
            oldest.closeStatements();
            prepared.remove(oldest);
        }
        statement.prepareStatements(connection);
        prepared.add(statement);
    }

    @Override
    public void addToBatch(TableRecord record) throws SQLException {
        JdbcTableWritePlan plan = routes.get(record.tableId());
        switch (record.rowKind()) {
            case UPDATE_BEFORE -> {
                if (beforeInExecution != null) {
                    throw new IllegalStateException("Nested JDBC UPDATE_BEFORE is not supported");
                }
                beforeInExecution = record;
            }
            case UPDATE_AFTER -> {
                TableRecord before = beforeInExecution;
                beforeInExecution = null;
                if (plan.writeMode() == JdbcWriteMode.RETRACT && before == null) {
                    throw new IllegalStateException("Keyless RETRACT has an incomplete UPDATE");
                }
                if (before != null) {
                    if (!before.tableId().equals(record.tableId())) {
                        throw new IllegalStateException("JDBC UPDATE pair crosses source tables");
                    }
                    if (plan.writeMode() == JdbcWriteMode.RETRACT && !reserve(record, plan)) {
                        return;
                    }
                    if (plan.writeMode() == JdbcWriteMode.RETRACT
                            || !plan.hasSamePrimaryKey(before.row(), record.row())) {
                        append(deletes.get(record.tableId()), before.row());
                    }
                }
                append(writes.get(record.tableId()), record.row());
            }
            case INSERT -> {
                if (plan.writeMode() != JdbcWriteMode.RETRACT || reserve(record, plan)) {
                    append(writes.get(record.tableId()), record.row());
                }
            }
            case DELETE -> {
                if (plan.writeMode() != JdbcWriteMode.RETRACT || reserve(record, plan)) {
                    append(deletes.get(record.tableId()), record.row());
                }
            }
        }
    }

    private boolean reserve(TableRecord record, JdbcTableWritePlan plan) throws SQLException {
        // Preserve the order of earlier DML before reserving the next mutation.
        if (active != null) {
            active.executeBatch();
            active = null;
        }
        return useReplayReceipts ? receipts.reserve(record, plan) : true;
    }

    private void append(TableSimpleStatementExecutor next, RowData row) throws SQLException {
        if (next == null) {
            throw new IllegalStateException("JDBC statement is unavailable for the record kind");
        }
        if (active != null && active != next) {
            active.executeBatch();
        }
        prepare(next);
        next.addToBatch(row);
        active = next;
    }

    @Override
    public void executeBatch() throws SQLException {
        if (beforeInExecution != null) {
            throw new IllegalStateException("Cannot execute an incomplete JDBC UPDATE pair");
        }
        if (active != null) {
            active.executeBatch();
            active = null;
        }
    }

    @Override
    public void closeStatements() throws SQLException {
        connection = null;
        SQLException failure = null;
        try {
            closePreparedStatements();
        } catch (SQLException error) {
            failure = error;
        }
        if (receipts != null) {
            try {
                receipts.close();
            } catch (SQLException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
            receipts = null;
        }
        if (failure != null) throw failure;
    }

    private void closePreparedStatements() throws SQLException {
        prepared.clear();
        active = null;
        beforeInExecution = null;
        awaitingUpdateEvent = null;
        SQLException failure = null;
        for (TableSimpleStatementExecutor statement : writes.values()) {
            try {
                statement.closeStatements();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        for (TableSimpleStatementExecutor statement : deletes.values()) {
            try {
                statement.closeStatements();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void cancel() throws SQLException {
        SQLException failure = null;
        if (receipts != null) {
            try {
                receipts.cancel();
            } catch (SQLException error) {
                failure = error;
            }
        }
        for (TableSimpleStatementExecutor statement : writes.values()) {
            try {
                statement.cancel();
            } catch (SQLException exception) {
                failure = exception;
            }
        }
        for (TableSimpleStatementExecutor statement : deletes.values()) {
            try {
                statement.cancel();
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
