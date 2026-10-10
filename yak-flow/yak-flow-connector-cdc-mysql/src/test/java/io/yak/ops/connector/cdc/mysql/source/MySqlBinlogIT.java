package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Opt-in real MySQL Binlog acceptance: current-position start, multi-table changes and recovery.
 *
 * <p>Run only against a disposable server with ROW Binlog, FULL row image and replication
 * permissions. No snapshot/backfill or external Sink is exercised by this test.
 */
class MySqlBinlogIT {

    private static final TableId ORDERS = new TableId("yak_cdc_it", null, "orders");
    private static final TableId ITEMS = new TableId("yak_cdc_it", null, "items");
    private static final String HOST = "127.0.0.1";
    private static final String USER = "root";

    @Test
    void streamsBinlogAndRestoresFromMailboxEmittedOffset() throws Exception {
        if (!Boolean.getBoolean("mysql.cdc.it.enabled")) {
            throw new IllegalStateException("Explicit -Dmysql.cdc.it.enabled=true required for MySQL Binlog IT");
        }
        String password = System.getProperty("mysql.cdc.it.password", "rootpass");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + HOST + ":3306/yak_cdc_it?useSSL=false&allowPublicKeyRetrieval=true",
                USER,
                password);
                Statement ddl = connection.createStatement()) {
            ddl.execute("DROP TABLE IF EXISTS orders");
            ddl.execute("DROP TABLE IF EXISTS items");
            ddl.execute("CREATE TABLE orders(ID BIGINT PRIMARY KEY, NAME VARCHAR(100))");
            ddl.execute("CREATE TABLE items(ID BIGINT PRIMARY KEY, NAME VARCHAR(100))");
        }

        TableSchema schema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(100))),
                List.of("ID"));
        MySqlCdcSource source = MySqlCdcSource.builder()
                .hostname(HOST)
                .username(USER)
                .password(password)
                .serverId(5551)
                .topicPrefix("yak_cdc_acceptance")
                .table(ORDERS, schema)
                .table(ITEMS, schema)
                .build();

        List<TableRecord> firstEvents = new ArrayList<>();
        MySqlBinlogSplit checkpoint;
        try (SourceReader<TableRecord, MySqlBinlogSplit> reader = createReader(source, null)) {
            // Bootstrap captures table schemas but no table rows, then starts the Binlog stream.
            Thread.sleep(6_000);
            execute(password, "INSERT INTO orders VALUES(1, 'first')");
            execute(password, "INSERT INTO items VALUES(10, 'item')");
            receive(reader, firstEvents, 2, Duration.ofSeconds(45));
            assertEquals(Set.of(ORDERS, ITEMS), Set.of(firstEvents.get(0).tableId(), firstEvents.get(1).tableId()));
            assertEquals(RowKind.INSERT, firstEvents.get(0).rowKind());
            checkpoint = reader.snapshotState(1L).getFirst();
            assertTrue(checkpoint.schemaHistory().length > 0);
            assertTrue(checkpoint.offset() != null);
            byte[] bytes = source.getSplitSerializer().serialize(checkpoint);
            checkpoint = source.getSplitSerializer().deserialize(source.getSplitSerializer().getVersion(), bytes);
        }

        List<TableRecord> recoveredEvents = new ArrayList<>();
        try (SourceReader<TableRecord, MySqlBinlogSplit> reader = createReader(source, checkpoint)) {
            Thread.sleep(3_000);
            execute(password, "UPDATE orders SET NAME='changed' WHERE ID=1");
            execute(password, "DELETE FROM items WHERE ID=10");
            receive(reader, recoveredEvents, 3, Duration.ofSeconds(45));
            assertEquals(
                    List.of(RowKind.UPDATE_BEFORE, RowKind.UPDATE_AFTER, RowKind.DELETE),
                    recoveredEvents.stream().map(TableRecord::rowKind).toList());
            assertEquals("changed", recoveredEvents.get(1).row().getString(1));
            assertEquals(ORDERS, recoveredEvents.get(0).tableId());
            assertEquals(ITEMS, recoveredEvents.get(2).tableId());
            assertFalse(reader.snapshotState(2L).isEmpty());
        }
    }

    private static SourceReader<TableRecord, MySqlBinlogSplit> createReader(
            MySqlCdcSource source, MySqlBinlogSplit restored) throws Exception {
        var context = new TestReaderContext();
        SourceReader<TableRecord, MySqlBinlogSplit> reader = source.createReader(context);
        reader.start();
        MySqlBinlogSplit split;
        if (restored == null) {
            var coordinator = new TestEnumeratorContext();
            var enumerator = source.createEnumerator(coordinator);
            enumerator.start();
            enumerator.handleSplitRequest(0);
            MySqlPendingSplitsState state = enumerator.snapshotState(1L);
            assertTrue(state.splitAssigned());
            split = coordinator.assigned;
            enumerator.close();
        } else {
            split = restored;
        }
        reader.addSplits(List.of(split));
        return reader;
    }

    private static void execute(String password, String query) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://" + HOST + ":3306/yak_cdc_it?useSSL=false&allowPublicKeyRetrieval=true",
                        USER,
                        password);
                Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    private static void receive(
            SourceReader<TableRecord, MySqlBinlogSplit> reader,
            List<TableRecord> rows,
            int minimum,
            Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        ReaderOutput<TableRecord> collector = rows::add;
        while (rows.size() < minimum && Instant.now().isBefore(deadline)) {
            InputStatus result = reader.pollNext(collector);
            if (result == InputStatus.END_OF_INPUT) {
                throw new AssertionError("Unbounded CDC reader ended unexpectedly");
            }
            if (result == InputStatus.NOTHING_AVAILABLE) {
                Thread.sleep(100);
            }
        }
        assertTrue(rows.size() >= minimum, "MySQL CDC did not deliver expected Binlog changes");
    }

    private static final class TestReaderContext implements SourceReaderContext {
        @Override
        public Configuration getConfiguration() {
            return new Configuration();
        }

        @Override
        public int getIndexOfSubtask() {
            return 0;
        }

        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public void sendSplitRequest() {
            // The acceptance fixture passes the assigned split directly after constructing it.
        }
    }

    private static final class TestEnumeratorContext implements SplitEnumeratorContext<MySqlBinlogSplit> {
        private MySqlBinlogSplit assigned;

        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public Set<Integer> registeredReaders() {
            return Set.of(0);
        }

        @Override
        public void assignSplit(MySqlBinlogSplit split, int subtaskId) {
            assigned = split;
        }

        @Override
        public void signalNoMoreSplits(int subtaskId) {
            throw new AssertionError("Unbounded Binlog work must remain active");
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            throw new AssertionError("Binlog-only mode does not discover JDBC splits");
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }
    }
}
