package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.configuration.CheckpointingOptions;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Opt-in real MySQL Hybrid acceptance for bounded chunks, replay and checkpoint handoff.
 *
 * <p>The fixture injects writes on both tables immediately after the Binlog low watermark
 * is captured, before snapshot planning. Real Debezium, Binlog and JDBC Snapshot I/O run
 * on Connector Base fetchers under deterministic mailbox/coordinator scheduling.
 */
class MySqlHybridIT {

    private static final String DATABASE = "yak_cdc_it";
    private static final TableId ORDERS = new TableId(DATABASE, null, "hybrid_orders");
    private static final TableId ITEMS = new TableId(DATABASE, null, "hybrid_items");

    @Test
    void capturesMultiTableConcurrentWritesAndDefersBinlogUntilCompletedSnapshotCheckpoint() throws Exception {
        if (!Boolean.getBoolean("mysql.cdc.hybrid.it.enabled")) {
            throw new IllegalStateException("Explicit -Dmysql.cdc.hybrid.it.enabled=true required");
        }
        String password = System.getProperty("mysql.cdc.it.password", "rootpass");
        execute(password, "DROP TABLE IF EXISTS hybrid_orders");
        execute(password, "CREATE TABLE hybrid_orders(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        execute(password, "INSERT INTO hybrid_orders VALUES (1,'old'),(100,'deleted')");
        execute(password, "DROP TABLE IF EXISTS hybrid_items");
        execute(password, "CREATE TABLE hybrid_items "
                + "(id BIGINT PRIMARY KEY, sku VARCHAR(100) NOT NULL, qty BIGINT NOT NULL)");
        execute(password, "INSERT INTO hybrid_items VALUES (10,'ten',2),(20,'removed',7)");

        TableSchema schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(100).copy(false))),
                List.of("id"));
        TableSchema itemSchema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("sku", LogicalTypes.varchar(100).copy(false)),
                        new Column("qty", LogicalTypes.BIGINT.copy(false))),
                List.of("id"));
        MySqlHybridCdcSource source = MySqlCdcSource.builder()
                .hostname("127.0.0.1")
                .username("root")
                .password(password)
                .serverId(5561)
                .topicPrefix("yak_hybrid_acceptance")
                .table(ORDERS, schema)
                .table(ITEMS, itemSchema)
                .buildHybrid(1);

        try (Harness harness = new Harness(source, 2, password)) {
            harness.start();
            harness.until(() -> harness.coordinatorState().phase() == MySqlHybridEnumeratorState.Phase.HANDOFF,
                    Duration.ofSeconds(80));

            // Before the completed checkpoint, nothing but the Snapshot INSERTs may reach the Sink.
            assertTrue(harness.output.stream().allMatch(row -> row.rowKind() == RowKind.INSERT));
            assertTrue(harness.changedDuringSnapshot);
            assertNotNull(harness.coordinatorState().lowWatermark());
            assertNotNull(harness.coordinatorState().highWatermark());

            for (SourceReader<TableRecord, MySqlHybridSplit> reader : harness.readers) {
                List<MySqlHybridSplit> snapshot = reader.snapshotState(30L);
                for (MySqlHybridSplit split : snapshot) {
                    byte[] encoded = source.getSplitSerializer().serialize(split);
                    assertNotNull(source.getSplitSerializer()
                            .deserialize(source.getSplitSerializer().getVersion(), encoded));
                }
            }
            MySqlHybridEnumeratorState beforeComplete = harness.enumerator.snapshotState(30L);
            assertEquals(MySqlHybridEnumeratorState.Phase.HANDOFF, beforeComplete.phase());
            harness.enumerator.notifyCheckpointComplete(30L);
            harness.until(() ->
                    harness.output.stream().anyMatch(row ->
                            ORDERS.equals(row.tableId()) && row.rowKind() == RowKind.DELETE)
                            && harness.output.stream().anyMatch(row ->
                                    ORDERS.equals(row.tableId()) && row.rowKind() == RowKind.UPDATE_AFTER)
                            && harness.output.stream().anyMatch(row ->
                                    ITEMS.equals(row.tableId()) && row.rowKind() == RowKind.DELETE)
                            && harness.output.stream().anyMatch(row ->
                                    ITEMS.equals(row.tableId()) && row.rowKind() == RowKind.UPDATE_AFTER)
                            && harness.output.stream().filter(row -> row.rowKind() == RowKind.INSERT).count() >= 6,
                    Duration.ofSeconds(80));

            Map<Long, String> orders = new LinkedHashMap<>();
            Map<Long, ItemValue> items = new LinkedHashMap<>();
            Long orderBeforeKey = null;
            Long itemBeforeKey = null;
            for (TableRecord record : harness.output) {
                long key = (Long) record.row().getField(0);
                if (ORDERS.equals(record.tableId())) {
                    switch (record.rowKind()) {
                        case INSERT -> orders.put(key, record.row().getString(1));
                        case UPDATE_BEFORE -> orderBeforeKey = key;
                        case UPDATE_AFTER -> {
                            if (orderBeforeKey != null && orderBeforeKey != key) {
                                orders.remove(orderBeforeKey);
                            }
                            orderBeforeKey = null;
                            orders.put(key, record.row().getString(1));
                        }
                        case DELETE -> orders.remove(key);
                    }
                } else if (ITEMS.equals(record.tableId())) {
                    switch (record.rowKind()) {
                        case INSERT -> items.put(key, itemValue(record));
                        case UPDATE_BEFORE -> itemBeforeKey = key;
                        case UPDATE_AFTER -> {
                            if (itemBeforeKey != null && itemBeforeKey != key) {
                                items.remove(itemBeforeKey);
                            }
                            itemBeforeKey = null;
                            items.put(key, itemValue(record));
                        }
                        case DELETE -> items.remove(key);
                    }
                } else {
                    throw new AssertionError("Unexpected source table: " + record.tableId());
                }
            }
            assertEquals(Map.of(1L, "changed", 5L, "inserted"), orders);
            assertEquals(Map.of(10L, new ItemValue("changed-sku", 3L),
                    30L, new ItemValue("added", 4L)), items);
            assertEquals(MySqlHybridEnumeratorState.Phase.STREAMING, harness.coordinatorState().phase());
        }
    }

    private static ItemValue itemValue(TableRecord record) {
        return new ItemValue(record.row().getString(1), (Long) record.row().getField(2));
    }

    private record ItemValue(String sku, long quantity) {}

    private static void execute(String password, String query) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        "jdbc:mysql://127.0.0.1:3306/" + DATABASE + "?useSSL=false&allowPublicKeyRetrieval=true",
                        "root",
                        password);
                Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private interface Condition {
        boolean met() throws Exception;
    }

    private static final class Harness implements AutoCloseable {
        private final MySqlHybridCdcSource source;
        private final String password;
        private final Deque<CheckedAction> events = new ArrayDeque<>();
        private final List<TableRecord> output = new ArrayList<>();
        private final List<SourceReader<TableRecord, MySqlHybridSplit>> readers = new ArrayList<>();
        private final SplitEnumerator<MySqlHybridSplit, MySqlHybridEnumeratorState> enumerator;
        private boolean changedDuringSnapshot;

        private Harness(MySqlHybridCdcSource source, int parallelism, String password) {
            this.source = source;
            this.password = password;
            enumerator = source.createEnumerator(new TestEnumeratorContext(parallelism));
            for (int id = 0; id < parallelism; id++) {
                readers.add(source.createReader(new TestReaderContext(id, parallelism)));
            }
        }

        private void start() throws Exception {
            enumerator.start();
            for (int id = 0; id < readers.size(); id++) {
                enumerator.addReader(id);
                readers.get(id).start();
            }
        }

        private MySqlHybridEnumeratorState coordinatorState() throws Exception {
            return enumerator.snapshotState(29);
        }

        private void until(Condition condition, Duration timeout) throws Exception {
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                while (!events.isEmpty()) {
                    events.removeFirst().run();
                }
                for (SourceReader<TableRecord, MySqlHybridSplit> reader : readers) {
                    InputStatus status = reader.pollNext(output::add);
                    assertFalse(status == InputStatus.END_OF_INPUT, "Hybrid Reader cannot reach EOF");
                }
                if (condition.met()) {
                    return;
                }
                Thread.sleep(35);
            }
            throw new AssertionError("Timed out while waiting for Hybrid Snapshot and Binlog handoff");
        }

        @Override
        public void close() throws Exception {
            Exception problem = null;
            for (SourceReader<TableRecord, MySqlHybridSplit> reader : readers) {
                try {
                    reader.close();
                } catch (Exception error) {
                    problem = error;
                }
            }
            try {
                enumerator.close();
            } catch (Exception error) {
                if (problem == null) {
                    problem = error;
                } else {
                    problem.addSuppressed(error);
                }
            }
            if (problem != null) {
                throw problem;
            }
        }

        private final class TestReaderContext implements SourceReaderContext {

            private final int id;
            private final int parallelism;

            private TestReaderContext(int id, int parallelism) {
                this.id = id;
                this.parallelism = parallelism;
            }

            @Override
            public Configuration getConfiguration() {
                return new Configuration().set(
                        CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(2));
            }

            @Override
            public int getIndexOfSubtask() {
                return id;
            }

            @Override
            public int currentParallelism() {
                return parallelism;
            }

            @Override
            public void sendSplitRequest() {
                events.addLast(() -> enumerator.handleSplitRequest(id));
            }

            @Override
            public void sendSourceEventToCoordinator(SourceEvent event) {
                events.addLast(() -> {
                    enumerator.handleSourceEvent(id, event);
                    if (event instanceof MySqlLowWatermarkEvent && !changedDuringSnapshot) {
                        changedDuringSnapshot = true;
                        execute(password, "UPDATE hybrid_orders SET name='changed' WHERE id=1");
                        execute(password, "DELETE FROM hybrid_orders WHERE id=100");
                        execute(password, "INSERT INTO hybrid_orders VALUES (5,'inserted')");
                        execute(password, "UPDATE hybrid_items SET sku='changed-sku', qty=3 WHERE id=10");
                        execute(password, "DELETE FROM hybrid_items WHERE id=20");
                        execute(password, "INSERT INTO hybrid_items VALUES (30,'added',4)");
                    }
                });
            }
        }

        private final class TestEnumeratorContext implements SplitEnumeratorContext<MySqlHybridSplit> {

            private final int parallelism;

            private TestEnumeratorContext(int parallelism) {
                this.parallelism = parallelism;
            }

            @Override
            public int currentParallelism() {
                return parallelism;
            }

            @Override
            public Set<Integer> registeredReaders() {
                return parallelism == 2 ? Set.of(0, 1) : Set.of(0);
            }

            @Override
            public void assignSplit(MySqlHybridSplit split, int subtaskId) {
                events.addLast(() -> readers.get(subtaskId).addSplits(List.of(split)));
            }

            @Override
            public void signalNoMoreSplits(int subtaskId) {
                throw new AssertionError("Unbounded Hybrid Source must not announce EOF");
            }

            @Override
            public void sendEventToSourceReader(int subtaskId, SourceEvent event) {
                events.addLast(() -> readers.get(subtaskId).handleSourceEvents(event));
            }

            @Override
            public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
                try {
                    handler.accept(action.call(), null);
                } catch (Exception error) {
                    handler.accept(null, error);
                }
            }

            @Override
            public void runInCoordinatorThread(Runnable action) {
                action.run();
            }
        }
    }
}
