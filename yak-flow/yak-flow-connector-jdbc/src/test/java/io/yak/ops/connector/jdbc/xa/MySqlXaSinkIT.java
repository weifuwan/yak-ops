package io.yak.ops.connector.jdbc.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real MySQL tests for prepared visibility, crash recovery, duplicate rows and orphan rollback. */
@Tag("jdbc")
@Tag("mysql")
@Timeout(180)
class MySqlXaSinkIT {

    @RegisterExtension
    static final MySqlDatabase DATABASE = new MySqlDatabase();

    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("amount", LogicalTypes.decimal(14, 2))),
            List.of());
    private static final SinkWriter.Context CONTEXT = new SinkWriter.Context() {
        @Override
        public Long timestamp() {
            return null;
        }

        @Override
        public long currentWatermark() {
            return Long.MIN_VALUE;
        }
    };

    @Test
    void preparedRowsRemainInvisibleUntilCheckpointCommitAndDoNotNeedReceipts() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId target = table(connection);
            create(connection, target);
            try {
                String owner = owner();
                MySqlXaWriter writer = writer(target, owner);
                MySqlXaCommitter committer = committer(owner);
                try {
                    writer.write(record(target, RowKind.INSERT, 1, "7.00", "insert-1"), CONTEXT);
                    writer.write(record(target, RowKind.INSERT, 1, "7.00", "insert-2"), CONTEXT);
                    writer.flush(false);
                    assertEquals(0, count(connection, target), "XA batch flush must not commit rows");
                    var prepared = writer.prepareCommit(1);
                    assertEquals(1, prepared.size());
                    assertEquals(0, count(connection, target), "XA PREPARE must remain invisible");
                    committer.commit(prepared, false);
                    assertEquals(2, count(connection, target));
                } finally {
                    writer.close();
                    committer.close();
                }
            } finally {
                drop(connection, target);
            }
        }
    }

    @Test
    void recoveryCommitsDurablePreparedTransactionAndRollsBackAbandonedPrepare() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId target = table(connection);
            create(connection, target);
            try {
                String owner = owner();
                MySqlXaWriter abandoned = writer(target, owner);
                abandoned.write(record(target, RowKind.INSERT, 2, "8.00", "abandoned"), CONTEXT);
                abandoned.prepareCommit(1);
                abandoned.close();
                assertEquals(0, count(connection, target));
                try (MySqlXaCommitter committer = committer(owner)) {
                    committer.recover(List.of());
                    assertEquals(0, count(connection, target));
                }
                MySqlXaWriter preparedWriter = writer(target, owner);
                preparedWriter.write(record(target, RowKind.INSERT, 3, "9.00", "prepared"), CONTEXT);
                var durable = preparedWriter.prepareCommit(2);
                preparedWriter.close();
                assertEquals(0, count(connection, target));
                try (MySqlXaCommitter restored = committer(owner)) {
                    restored.recover(durable);
                    restored.commit(durable, true);
                }
                assertEquals(1, count(connection, target));
            } finally {
                drop(connection, target);
            }
        }
    }

    @Test
    void commitAcknowledgmentLossDoesNotDuplicateTheRecoveredTransaction() throws Exception {
        try (Connection connection = DATABASE.metadata().connect()) {
            TableId target = table(connection);
            create(connection, target);
            try {
                String owner = owner();
                try (MySqlXaWriter writer = writer(target, owner);
                        MySqlXaCommitter committer = committer(owner)) {
                    writer.write(record(target, RowKind.INSERT, 4, "15.00", "one-event"), CONTEXT);
                    var prepared = writer.prepareCommit(3);
                    committer.commit(prepared, false);
                    assertEquals(1, count(connection, target));
                    // The next attempt only knows the committable from its durable checkpoint.
                    // A missing XA XID on recovery must not execute the old DML again.
                    committer.recover(prepared);
                    committer.commit(prepared, true);
                    assertEquals(1, count(connection, target));
                }
            } finally {
                drop(connection, target);
            }
        }
    }

    private static String owner() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static TableId table(Connection connection) throws Exception {
        return new TableId(connection.getCatalog(), null, TestNames.unique("xa_retract"));
    }

    private static MySqlXaWriter writer(TableId table, String owner) {
        return new MySqlXaWriter(
                connectionOptions(),
                new MySqlJdbcDialect(),
                new JdbcTableWritePlan(table, table, SCHEMA, JdbcWriteMode.RETRACT),
                owner,
                0);
    }

    private static MySqlXaCommitter committer(String owner) {
        return new MySqlXaCommitter(MySqlXaSink.dataSource(connectionOptions()), owner);
    }

    private static JdbcConnectionOptions connectionOptions() {
        var db = DATABASE.metadata();
        return new JdbcConnectionOptions(db.url(), db.username(), db.password());
    }

    private static TableRecord record(TableId table, RowKind kind, long bucket, String amount, String event) {
        return new TableRecord(
                        table, kind, GenericRowData.of(bucket, new BigDecimal(amount)))
                .withChangeEventId(new ChangeEventId("source-" + table.table(), event, 0, 1));
    }

    private static void create(Connection connection, TableId table) throws Exception {
        try (var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE " + new MySqlJdbcDialect().qualifiedTable(table)
                    + " (bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB");
        }
    }

    private static long count(Connection connection, TableId table) throws Exception {
        try (var sql = connection.createStatement();
                var rows = sql.executeQuery(
                        "SELECT COUNT(*) FROM " + new MySqlJdbcDialect().qualifiedTable(table))) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void drop(Connection connection, TableId table) throws Exception {
        try (var sql = connection.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS " + new MySqlJdbcDialect().qualifiedTable(table));
        }
    }
}
