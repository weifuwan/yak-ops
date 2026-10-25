package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.ReaderOutput;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Real MySQL Binlog integration: current-position start, multi-table changes and recovery.
 *
 * <p>Run only against a disposable server with ROW Binlog, FULL row image and replication
 * permissions. No snapshot/backfill or external Sink is exercised by this test.
 */
@Tag("cdc")
@Timeout(240)
class MySqlBinlogIT {
    @RegisterExtension
    static final MySqlDatabase DATABASE_RESOURCE = MySqlDatabase.cdc();

    private static final TableId ORDERS = new TableId("yak_cdc_it", null, "orders");
    private static final TableId ITEMS = new TableId("yak_cdc_it", null, "items");
    private static final String HOST = "127.0.0.1";
    private static final String USER = "root";

    @Test
    void snapshotSessionUsesUtcWithoutChangingGlobalTimeZone() throws Exception {
        var db = DATABASE_RESOURCE.metadata();
        var schema = new TableSchema(List.of(
                new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = new MySqlCdcSourceConfig(
                db.host(), db.port(), db.username(), db.password(), 7001, "tz-test",
                Map.of(ORDERS, schema), 256);
        String globalBefore;
        try (Connection connection = db.connect();
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT @@global.time_zone")) {
            assertTrue(result.next());
            globalBefore = result.getString(1);
        }
        try (Connection connection = config.openSnapshotConnection();
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT @@session.time_zone")) {
            assertTrue(result.next());
            assertTrue(List.of("+00:00", "UTC").contains(result.getString(1)));
        }
        try (Connection connection = db.connect();
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT @@global.time_zone")) {
            assertTrue(result.next());
            assertEquals(globalBefore, result.getString(1));
        }
    }

    @Test
    void streamsBinlogAndRestoresFromMailboxEmittedOffset() throws Exception {
        String password = DATABASE_RESOURCE.metadata().password();
        try (Connection connection =
                        DriverManager.getConnection(DATABASE_RESOURCE.metadata().url(), USER, password);
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
                .hostname(DATABASE_RESOURCE.metadata().host())
                .port(DATABASE_RESOURCE.metadata().port())
                .username(USER)
                .password(password)
                .serverId(5551)
                .topicPrefix("yak_cdc_integration")
                .table(ORDERS, schema)
                .table(ITEMS, schema)
                .build();

        List<TableRecord> firstEvents = new ArrayList<>();
        MySqlBinlogSplit checkpoint;
        try (SourceReader<TableRecord, MySqlBinlogSplit> reader = createReader(source, null)) {
            // Bootstrap captures table schemas but no table rows, then starts the Binlog stream.
            awaitReaderReady(reader);
            execute(password, "INSERT INTO orders VALUES(1, 'first')");
            execute(password, "INSERT INTO items VALUES(10, 'item')");
            receive(reader, firstEvents, 2, Duration.ofSeconds(45));
            assertEquals(
                    Set.of(ORDERS, ITEMS),
                    Set.of(firstEvents.get(0).tableId(), firstEvents.get(1).tableId()));
            assertEquals(RowKind.INSERT, firstEvents.get(0).rowKind());
            assertNotNull(firstEvents.get(0).changeEventId(), "Real Debezium row requires fine-grained Binlog identity");
            assertNotNull(firstEvents.get(1).changeEventId(), "Second Binlog row requires stable identity");
            assertFalse(firstEvents.get(0).changeEventId().sameEvent(firstEvents.get(1).changeEventId()));

            // One SQL statement can produce multiple row events with the same Binlog file/pos.
            // Their per-row Debezium coordinates, not their identical payloads, must differ.
            execute(password, "INSERT INTO orders VALUES(2, 'same'), (3, 'same')");
            receive(reader, firstEvents, 4, Duration.ofSeconds(45));
            assertEquals("same", firstEvents.get(2).row().getString(1));
            assertEquals("same", firstEvents.get(3).row().getString(1));
            assertNotNull(firstEvents.get(2).changeEventId());
            assertNotNull(firstEvents.get(3).changeEventId());
            assertFalse(firstEvents.get(2).changeEventId().sameEvent(firstEvents.get(3).changeEventId()));
            checkpoint = reader.snapshotState(1L).getFirst();
            assertTrue(checkpoint.schemaHistory().length > 0);
            assertTrue(checkpoint.offset() != null);
            byte[] bytes = source.getSplitSerializer().serialize(checkpoint);
            checkpoint = source.getSplitSerializer()
                    .deserialize(source.getSplitSerializer().getVersion(), bytes);
        }

        List<TableRecord> recoveredEvents = new ArrayList<>();
        try (SourceReader<TableRecord, MySqlBinlogSplit> reader = createReader(source, checkpoint)) {
            awaitReaderReady(reader);
            execute(password, "UPDATE orders SET NAME='changed' WHERE ID=1");
            execute(password, "DELETE FROM items WHERE ID=10");
            receive(reader, recoveredEvents, 3, Duration.ofSeconds(45));
            assertEquals(
                    List.of(RowKind.UPDATE_BEFORE, RowKind.UPDATE_AFTER, RowKind.DELETE),
                    recoveredEvents.stream().map(TableRecord::rowKind).toList());
            assertEquals("changed", recoveredEvents.get(1).row().getString(1));
            assertNotNull(recoveredEvents.get(0).changeEventId(), "UPDATE must have a source event position");
            assertEquals(recoveredEvents.get(0).changeEventId().eventId(),
                    recoveredEvents.get(1).changeEventId().eventId());
            assertEquals(0, recoveredEvents.get(0).changeEventId().imageIndex());
            assertEquals(1, recoveredEvents.get(1).changeEventId().imageIndex());
            assertEquals(2, recoveredEvents.get(1).changeEventId().imageCount());
            assertNotNull(recoveredEvents.get(2).changeEventId(), "DELETE must have a source event position");
            assertEquals(ORDERS, recoveredEvents.get(0).tableId());
            assertEquals(ITEMS, recoveredEvents.get(2).tableId());
            assertFalse(reader.snapshotState(2L).isEmpty());
        }
    }

    @Test
    void realMultiRowTransactionIdentityRemainsUniqueAcrossBinlogRotation() throws Exception {
        String password = DATABASE_RESOURCE.metadata().password();
        try (Connection connection = DATABASE_RESOURCE.metadata().connect();
                Statement ddl = connection.createStatement()) {
            ddl.execute("DROP TABLE IF EXISTS orders");
            ddl.execute("CREATE TABLE orders(ID BIGINT PRIMARY KEY, NAME VARCHAR(100))");
        }

        var schema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(100))),
                List.of("ID"));
        var source = MySqlCdcSource.builder()
                .hostname(DATABASE_RESOURCE.metadata().host())
                .port(DATABASE_RESOURCE.metadata().port())
                .username(USER)
                .password(password)
                .serverId(5553)
                .topicPrefix("yak_cdc_identity_rotation")
                .table(ORDERS, schema)
                .build();

        try (SourceReader<TableRecord, MySqlBinlogSplit> reader = createReader(source, null)) {
            awaitReaderReady(reader);
            execute(password, "INSERT INTO orders VALUES (110, 'same'), (111, 'same'), (112, 'same')");
            List<TableRecord> rows = new ArrayList<>();
            receive(reader, rows, 3, Duration.ofSeconds(45));
            assertEquals(3, rows.size());
            var beforeRotation = rows.stream()
                    .map(TableRecord::changeEventId)
                    .toList();
            assertTrue(beforeRotation.stream().allMatch(java.util.Objects::nonNull));
            assertEquals(3, new java.util.HashSet<>(beforeRotation).size());

            execute(password, "FLUSH BINARY LOGS");
            execute(password, "INSERT INTO orders VALUES (113, 'same'), (114, 'same')");
            receive(reader, rows, 5, Duration.ofSeconds(45));
            assertTrue(rows.stream().allMatch(record -> record.changeEventId() != null));
            assertEquals(5, new java.util.HashSet<>(
                    rows.stream().map(TableRecord::changeEventId).toList()).size());

            MySqlBinlogSplit checkpoint = reader.snapshotState(22L).getFirst();
            assertNotNull(checkpoint.offset());
            assertFalse(checkpoint.schemaHistory().length == 0);
        }
    }

    /** The owning Reader must have consumed the post-bootstrap heartbeat before test DML.
     *
     * <p>A server-wide SHOW PROCESSLIST Binlog Dump belongs to an arbitrary replication
     * client and is not evidence that this Reader has finished its schema snapshot.
     */
    private static void awaitReaderReady(SourceReader<TableRecord, MySqlBinlogSplit> reader) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            if (reader.pollNext(record -> {
                throw new AssertionError("Unexpected row before first test mutation: " + record.tableId());
            }) == InputStatus.END_OF_INPUT) {
                throw new AssertionError("Unbounded Binlog Reader ended during bootstrap");
            }
            try {
                List<MySqlBinlogSplit> splits = reader.snapshotState(0L);
                if (splits.size() == 1
                        && splits.getFirst().offset() != null
                        && splits.getFirst().schemaHistory().length > 0) {
                    return;
                }
            } catch (IllegalStateException notReady) {
                if (notReady.getMessage() == null
                        || !notReady.getMessage().contains("no complete offset/schema-history checkpoint")) {
                    throw notReady;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Own MySQL CDC Reader did not complete no-data bootstrap");
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
        try (Connection connection =
                        DriverManager.getConnection(DATABASE_RESOURCE.metadata().url(), USER, password);
                Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    private static void receive(
            SourceReader<TableRecord, MySqlBinlogSplit> reader, List<TableRecord> rows, int minimum, Duration timeout)
            throws Exception {
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
            // The integration fixture passes the assigned split directly after constructing it.
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
