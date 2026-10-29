package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.core.api.connector.source.ReaderInfo;
import io.yak.ops.core.api.connector.source.SplitsAssignment;
import io.yak.ops.core.api.metrics.Gauge;
import io.yak.ops.core.api.metrics.groups.SplitEnumeratorMetricGroup;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.assigners.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.PendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsReportEvent;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests actual Enumerator async callbacks, durable cursors and reader assignments. */
class MySqlSourceEnumeratorTest {
    private static final TableId ORDERS = new TableId("shop", null, "orders");
    private static final PendingSplitsStateSerializer SERIALIZER =
            new PendingSplitsStateSerializer(io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer.INSTANCE);

    @Test
    void assignsTypedChunksAndResumesAtCheckpointedBoundaryWithoutDuplicatingPriorWork() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA shop");
            statement.execute("CREATE TABLE shop.orders (id BIGINT PRIMARY KEY, bucket DECIMAL(30,8) NOT NULL)");
            statement.execute("INSERT INTO shop.orders VALUES "
                    + "(1,1.00000000),(2,1.00000000),"
                    + "(3,3.00000000),(4,7.00000000),(5,7.00000000),(6,10.00000000)");
        }
        var keyed = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("bucket", LogicalTypes.decimal(30, 8).copy(false))),
                List.of("id"));
        var config = configuration(keyed, "bucket");
        var context = new Coordinator();
        var enumerator = enumerator(context, config, url, null);
        enumerator.start();
        enumerator.handleSplitRequest(0, null);
        enumerator.handleSplitRequest(1, null);
        assertEquals(1, context.pendingActions());
        context.runOne();

        MySqlSnapshotSplit first = (MySqlSnapshotSplit) context.assignments(0).getFirst();
        assertEquals("mysql-snapshot-0-0", first.splitId());
        assertEquals(LogicalTypeRoot.DECIMAL, first.chunkRange().type());
        assertNull(first.chunkRange().start());
        assertEquals(new BigDecimal("3.00000000"), first.chunkRange().end());

        var saved = (HybridPendingSplitsState) SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize((HybridPendingSplitsState) enumerator.snapshotState(5)));
        assertEquals(0, saved.getSnapshotPendingSplits().nextTableIndex());
        assertEquals(1, saved.getSnapshotPendingSplits().nextChunkIndex());
        assertEquals(first.chunkRange().end(), saved.getSnapshotPendingSplits().nextChunkRange().start());
        assertTrue(saved.getSnapshotPendingSplits().getAssignedSplits().containsKey(first.splitId()));
        enumerator.close();

        var secondContext = new Coordinator();
        var recovered = enumerator(secondContext, config, url, saved);
        recovered.start();
        assertEquals(1, secondContext.pendingActions());
        secondContext.runOne();
        recovered.handleSplitRequest(1, null);
        MySqlSnapshotSplit second = (MySqlSnapshotSplit) secondContext.assignments(1).getFirst();
        assertEquals("mysql-snapshot-0-1", second.splitId());
        assertEquals(first.chunkRange().end(), second.chunkRange().start());
        assertNotEquals(first.splitId(), second.splitId());
        assertFalse(secondContext.assignments(1).contains(first));
        recovered.handleSourceEvent(
                1,
                new FinishedSnapshotSplitsReportEvent(Map.of(first.splitId(), offset(150))));
        recovered.handleSourceEvent(
                1, new FinishedSnapshotSplitsReportEvent(Map.of(first.splitId(), offset(150))));
        assertThrows(IllegalArgumentException.class, () -> recovered.handleSourceEvent(
                1, new FinishedSnapshotSplitsReportEvent(Map.of("unknown-split", offset(150)))));
        var resumed = (HybridPendingSplitsState) SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize((HybridPendingSplitsState) recovered.snapshotState(6)));
        assertEquals(first.chunkRange(), resumed.getSnapshotPendingSplits().getFinishedSplitInfos().getFirst().chunkRange());
        assertEquals(offset(150), resumed.getSnapshotPendingSplits().getFinishedSplitInfos().getFirst().highWatermark());
        recovered.close();
    }

    @Test
    void doesNotAssignDeferredBinlogToSnapshotReaderWhenReaderZeroIsUnavailable() throws Exception {
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        var snapshot = MySqlSnapshotSplit.typed(
                "snapshot-0", new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, null, null));
        assigner.plan(List.of(snapshot));
        assertEquals(snapshot, assigner.nextSnapshot());
        var high = offset(200);
        assigner.finishSnapshot(new io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo(
                ORDERS, snapshot.splitId(), null, null, high, snapshot.chunkRange()));
        assigner.captureSnapshotSchemaHistory(
                Map.of(snapshot.splitId(), high), Map.of(snapshot.splitId(), new byte[] {1, 2, 3}));
        assigner.captureHigh(high);
        assigner.snapshotState(1);
        assigner.notifyCheckpointComplete(1);
        var context = new Coordinator() {
            @Override
            public Map<Integer, ReaderInfo> registeredReaders() {
                return Map.of(1, new ReaderInfo(1, ""));
            }
        };
        var coordinator = enumerator(context, configuration(schema, null), database(), assigner.snapshotState(2));
        coordinator.start();
        coordinator.handleSplitRequest(1, null);
        assertTrue(context.assignments(1).isEmpty());
        var state = (HybridPendingSplitsState) coordinator.snapshotState(3);
        assertFalse(state.isBinlogSplitAssigned());
        coordinator.close();
    }

    @Test
    void plansKeylessDecimalChunksWithNoPhysicalPrimaryKeyAndPreservesDiscoveryPosition() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA shop");
            statement.execute("CREATE TABLE shop.orders (bucket DECIMAL(30,8) NOT NULL, payload INT)");
            statement.execute("INSERT INTO shop.orders VALUES "
                    + "(1.00000000,1),(1.00000000,2),"
                    + "(9223372036854775808.00000000,3),(9223372036854775808.00000000,4)");
        }
        var keyless = new TableSchema(
                List.of(
                        new Column("bucket", LogicalTypes.decimal(30, 8).copy(false)),
                        new Column("payload", LogicalTypes.INTEGER)),
                List.of());
        var planner = splitter(url, keyless, "bucket");
        var assigner = new MySqlHybridSplitAssigner(StartupOptions.initial(), null);
        MySqlSnapshotSplit first = planner.planNext(ORDERS, 0, 0, null);
        assertEquals(new BigDecimal("9223372036854775808.00000000"), first.chunkRange().end());
        var cursor = new ChunkRange(
                ORDERS, "bucket", LogicalTypeRoot.DECIMAL, first.chunkRange().end(), null);
        assigner.appendPlan(first, 1);
        var saved = (HybridPendingSplitsState) SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize((HybridPendingSplitsState) assigner.snapshotState(9)));
        var restored = new MySqlHybridSplitAssigner(StartupOptions.initial(), saved);
        MySqlSnapshotSplit last = planner.planNext(ORDERS, restored.nextTableIndex(), restored.nextChunkIndex(),
                restored.nextChunkRange());
        assertEquals(cursor.start(), last.chunkRange().start());
        assertNull(last.chunkRange().end());
        restored.appendPlan(last, 1);
        assertTrue(restored.snapshotState(10).getSnapshotPendingSplits().snapshotPlanned());
        assertNull(restored.snapshotState(10).getSnapshotPendingSplits().nextChunkRange());
    }

    @Test
    void rejectsRestoredCursorFromDifferentTableOrChunkColumnWithoutOpeningJdbc() throws Exception {
        String url = database();
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("bucket", LogicalTypes.INTEGER.copy(false))),
                List.of("id"));
        var planner = splitter(url, schema, "bucket");
        assertThrows(IllegalArgumentException.class, () -> planner.planNext(ORDERS, 0, 1,
                new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, 2L, null)));
        assertThrows(IllegalArgumentException.class, () -> planner.planNext(ORDERS, 0, 1,
                new ChunkRange(new TableId("other", null, "orders"), "bucket", LogicalTypeRoot.INTEGER, 2L, null)));
        assertThrows(IllegalArgumentException.class,
                () -> planner.planNext(ORDERS, 0, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> planner.planNext(ORDERS, 0, 0,
                        new ChunkRange(ORDERS, "bucket", LogicalTypeRoot.INTEGER, 2L, null)));
    }

    @Test
    void bigintPrimaryKeyAlsoUsesTypedChunkDiscovery() throws Exception {
        String url = database();
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA shop");
            statement.execute("CREATE TABLE shop.orders (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO shop.orders VALUES (1),(3),(5)");
        }
        var keyed = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = configuration(keyed, null);
        var context = new Coordinator();
        var enumerator = enumerator(context, config, url, null);
        enumerator.start();
        enumerator.handleSplitRequest(0, null);
        enumerator.handleSplitRequest(1, null);
        context.runOne();
        var first = (MySqlSnapshotSplit) context.assignments(0).getFirst();
        assertEquals(LogicalTypeRoot.BIGINT, first.chunkRange().type());
        assertEquals(3L, first.chunkRange().end());
        var checkpoint = enumerator.snapshotState(1);
        assertEquals(3L, ((HybridPendingSplitsState) checkpoint).getSnapshotPendingSplits().nextChunkRange().start());
        enumerator.close();
    }

    private static MySqlSourceEnumerator enumerator(
            Coordinator context,
            MySqlSourceConfig config,
            String url,
            HybridPendingSplitsState restored) {
        return new MySqlSourceEnumerator(
                StartupOptions.initial(),
                context,
                config,
                2,
                restored,
                new TableMetrics(),
                new MySqlChunkSplitter(
                        () -> DriverManager.getConnection(url),
                        JdbcDialects.forUrl("jdbc:mysql:"),
                        config.schemas(),
                        config.chunkKeyColumns(),
                        2));
    }

    private static MySqlChunkSplitter splitter(String url, TableSchema schema, String chunkColumn) {
        return new MySqlChunkSplitter(
                () -> DriverManager.getConnection(url),
                JdbcDialects.forUrl("jdbc:mysql:"),
                Map.of(ORDERS, schema),
                Map.of(ORDERS, chunkColumn),
                2);
    }

    private static MySqlSourceConfig configuration(TableSchema schema, String chunkColumn) {
        var builder = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9001)
                .topicPrefix("typed-checkpoint-test");
        if (chunkColumn == null) builder.table(ORDERS, schema);
        else builder.table(ORDERS, schema, chunkColumn);
        return builder.build().config();
    }

    private static String database() {
        return "jdbc:h2:mem:typed_assignment_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static DebeziumOffset offset(long pos) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", pos));
    }

    private static class Coordinator implements SplitEnumeratorContext<MySqlSplit> {
        private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
        private final Map<Integer, List<MySqlSplit>> assigned = new java.util.HashMap<>();

        @Override
        public int currentParallelism() {
            return 2;
        }

        @Override
        public Map<Integer, ReaderInfo> registeredReaders() { return Map.of(0, new ReaderInfo(0, ""), 1, new ReaderInfo(1, "")); }

        @Override
        public SplitEnumeratorMetricGroup metricGroup() {
            return new SplitEnumeratorMetricGroup() {
                @Override
                public <G extends Gauge<Long>> G setUnassignedSplitsGauge(G gauge) {
                    return gauge;
                }
            };
        }

        @Override
        public void assignSplits(SplitsAssignment<MySqlSplit> assignment) {
            assignment.assignment().forEach((reader, splits) ->
                    splits.forEach(split -> assignSplit(split, reader)));
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler,
                long initialDelayMillis, long periodMillis) {
            throw new UnsupportedOperationException("Fixture does not schedule periodic discovery");
        }

        @Override
        public void assignSplit(MySqlSplit split, int reader) {
            assigned.computeIfAbsent(reader, key -> new ArrayList<>()).add(split);
        }

        @Override
        public void signalNoMoreSplits(int reader) {}

        @Override
        public void sendEventToSourceReader(int reader, SourceEvent event) {}

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            actions.addLast(() -> {
                T result = null;
                Throwable failure = null;
                try {
                    result = action.call();
                } catch (Throwable error) {
                    failure = error;
                }
                handler.accept(result, failure);
            });
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }

        int pendingActions() {
            return actions.size();
        }

        List<MySqlSplit> assignments(int reader) {
            return assigned.getOrDefault(reader, List.of());
        }

        void runOne() {
            assertFalse(actions.isEmpty());
            actions.removeFirst().run();
        }
    }
}
