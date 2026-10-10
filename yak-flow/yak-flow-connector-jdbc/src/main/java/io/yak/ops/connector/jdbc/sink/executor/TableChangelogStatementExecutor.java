package io.yak.ops.connector.jdbc.sink.executor;

import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Table-aware statement selection with input-order preservation.
 *
 * <p>This executor retains no records. Consecutive writes to one statement use a JDBC
 * driver batch; changing table or mutation kind executes the previous batch before adding
 * the next record. All statements share the OutputFormat's single transaction. This avoids
 * the reordering introduced by grouping independent table and key buffers.
 */
public final class TableChangelogStatementExecutor implements JdbcBatchStatementExecutor<TableRecord> {

    private final Map<TableId, JdbcTableWritePlan> routes = new LinkedHashMap<>();
    private final Map<TableId, TableSimpleStatementExecutor> writes = new LinkedHashMap<>();
    private final Map<TableId, TableSimpleStatementExecutor> deletes = new LinkedHashMap<>();
    private TableSimpleStatementExecutor active;
    private TableId awaitingUpdateAfter;
    private TableRecord beforeInExecution;

    public TableChangelogStatementExecutor(JdbcDialect dialect, List<JdbcTableWritePlan> plans) {
        Objects.requireNonNull(dialect, "dialect");
        if (Objects.requireNonNull(plans, "plans").isEmpty()) {
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
            }
        }
    }

    /**
     * Validates adjacent UPDATE_BEFORE / UPDATE_AFTER events before entering the sole row buffer.
     *
     * <p>Flink CDC carries both images in one event, while the current YakFlow TableRecord
     * carries one RowKind. Until an atomic update contract exists, split updates must arrive
     * consecutively on the same Sink subtask; malformed or interleaved pairs fail closed.
     */
    public TableRecord snapshot(TableRecord record) {
        Objects.requireNonNull(record, "record");
        JdbcTableWritePlan plan = routes.get(record.tableId());
        if (plan == null) {
            throw new IllegalArgumentException("JDBC Sink received an unknown source table");
        }
        if (awaitingUpdateAfter != null
                && (record.rowKind() != io.yak.ops.core.data.RowKind.UPDATE_AFTER
                        || !awaitingUpdateAfter.equals(record.tableId()))) {
            throw new IllegalArgumentException("UPDATE_BEFORE must be followed by UPDATE_AFTER for the same table");
        }
        TableRecord detached =
                new TableRecord(record.tableId(), record.rowKind(), plan.project(record.row(), record.rowKind()));
        if (record.rowKind() == io.yak.ops.core.data.RowKind.UPDATE_BEFORE) {
            awaitingUpdateAfter = record.tableId();
        } else if (awaitingUpdateAfter != null) {
            awaitingUpdateAfter = null;
        }
        return detached;
    }

    /** Only complete UPDATE pairs may be committed by an automatic batch trigger. */
    public boolean canAutomaticallyFlush() {
        return awaitingUpdateAfter == null;
    }

    /** A checkpoint must not acknowledge a partial UPDATE_BEFORE/UPDATE_AFTER pair. */
    public void requireCompleteUpdate() {
        if (awaitingUpdateAfter != null) {
            throw new IllegalStateException("Cannot checkpoint or finish an incomplete JDBC UPDATE pair");
        }
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        try {
            for (TableSimpleStatementExecutor statement : writes.values()) {
                statement.prepareStatements(connection);
            }
            for (TableSimpleStatementExecutor statement : deletes.values()) {
                statement.prepareStatements(connection);
            }
        } catch (SQLException | RuntimeException failure) {
            try {
                closeStatements();
            } catch (SQLException closingFailure) {
                failure.addSuppressed(closingFailure);
            }
            throw failure;
        }
    }

    @Override
    public void addToBatch(TableRecord record) throws SQLException {
        switch (record.rowKind()) {
            case UPDATE_BEFORE -> {
                if (beforeInExecution != null) {
                    throw new IllegalStateException("Nested JDBC UPDATE_BEFORE is not supported");
                }
                // Borrow a reference from the only row buffer for the duration of this flush.
                beforeInExecution = record;
            }
            case UPDATE_AFTER -> {
                if (beforeInExecution != null) {
                    TableRecord before = beforeInExecution;
                    beforeInExecution = null;
                    JdbcTableWritePlan plan = routes.get(record.tableId());
                    if (!before.tableId().equals(record.tableId())) {
                        throw new IllegalStateException("JDBC UPDATE pair crosses source tables");
                    }
                    if (!plan.hasSamePrimaryKey(before.row(), record.row())) {
                        append(deletes.get(record.tableId()), before.row());
                    }
                }
                append(writes.get(record.tableId()), record.row());
            }
            case INSERT -> append(writes.get(record.tableId()), record.row());
            case DELETE -> append(deletes.get(record.tableId()), record.row());
        }
    }

    private void append(TableSimpleStatementExecutor next, io.yak.ops.core.data.RowData row) throws SQLException {
        if (next == null) {
            throw new IllegalStateException("JDBC statement is unavailable for the record kind");
        }
        if (active != null && active != next) {
            active.executeBatch();
        }
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
        active = null;
        beforeInExecution = null;
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
