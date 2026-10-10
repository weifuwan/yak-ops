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

    /** Validate and detach the record once, before it enters the buffered executor. */
    public TableRecord snapshot(TableRecord record) {
        Objects.requireNonNull(record, "record");
        JdbcTableWritePlan plan = routes.get(record.tableId());
        if (plan == null) {
            throw new IllegalArgumentException("JDBC Sink received an unknown source table");
        }
        return new TableRecord(record.tableId(), record.rowKind(), plan.project(record.row(), record.rowKind()));
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
        TableSimpleStatementExecutor next =
                switch (record.rowKind()) {
                    case INSERT, UPDATE_AFTER -> writes.get(record.tableId());
                    case DELETE, UPDATE_BEFORE -> deletes.get(record.tableId());
                };
        if (next == null) {
            throw new IllegalStateException("JDBC statement is unavailable for the record kind");
        }
        if (active != null && active != next) {
            active.executeBatch();
        }
        next.addToBatch(record.row());
        active = next;
    }

    @Override
    public void executeBatch() throws SQLException {
        if (active != null) {
            active.executeBatch();
            active = null;
        }
    }

    @Override
    public void closeStatements() throws SQLException {
        active = null;
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
