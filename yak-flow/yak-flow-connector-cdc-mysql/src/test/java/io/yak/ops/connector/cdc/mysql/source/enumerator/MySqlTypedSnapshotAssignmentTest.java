package io.yak.ops.connector.cdc.mysql.source.enumerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.assigner.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlHybridSplitAssigner;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlSnapshotFinishedEvent;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Tests actual Enumerator async callbacks, durable cursors and reader assignments. */
class MySqlTypedSnapshotAssignmentTest {
    private static final TableId ORDERS = new TableId("shop", null, "orders");
    private static final String FINGERPRINT = "typed-enumerator-test";
    private static final MySqlHybridEnumeratorStateSerializer SERIALIZER =
            new MySqlHybridEnumeratorStateSerializer();

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
        enumerator.handleSplitRequest(0);
        enumerator.handleSplitRequest(1);
        enumerator.handleSourceEvent(0, new MySqlLowWatermarkEvent(offset(100)));
        assertEquals(1, context.pendingActions());
        context.runOne();

        MySqlSnapshotSplit first = (MySqlSnapshotSplit) context.assignments(1).getFirst();
        assertEquals("mysql-snapshot-0-0", first.splitId());
        assertEquals(LogicalTypeRoot.DECIMAL, first.chunkRange().type());
        assertNull(first.chunkRange().start());
        assertEquals(new BigDecimal("3.00000000"), first.chunkRange().end());

        var saved = SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(enumerator.snapshotState(5)));
        assertEquals(0, saved.nextTableIndex());
        assertEquals(1, saved.nextChunkIndex());
        assertEquals(first.chunkRange().end(), saved.nextChunkRange().start());
        assertTrue(saved.assignedSnapshots().containsKey(first.splitId()));
        enumerator.close();

        var secondContext = new Coordinator();
        var recovered = enumerator(secondContext, config, url, saved);
        recovered.start();
        assertEquals(1, secondContext.pendingActions());
        secondContext.runOne();
        recovered.handleSplitRequest(1);
        MySqlSnapshotSplit second = (MySqlSnapshotSplit) secondContext.assignments(1).getFirst();
        assertEquals("mysql-snapshot-0-1", second.splitId());
        assertEquals(first.chunkRange().end(), second.chunkRange().start());
        assertNotEquals(first.splitId(), second.splitId());
        assertFalse(secondContext.assignments(1).contains(first));
        recovered.handleSourceEvent(
                1,
                new MySqlSnapshotFinishedEvent(new FinishedSnapshotSplitInfo(
                        ORDERS, first.splitId(), null, null, offset(150), first.chunkRange())));
        var resumed = SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(recovered.snapshotState(6)));
        assertEquals(first.chunkRange(), resumed.finishedSplitInfos().getFirst().chunkRange());
        assertEquals(offset(150), resumed.finishedSplitInfos().getFirst().highWatermark());
        recovered.close();
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
        var assigner = new MySqlHybridSplitAssigner(FINGERPRINT, null);
        assigner.nextBinlog(0);
        assigner.captureLow(offset(100));
        MySqlSnapshotSplit first = planner.planNext(ORDERS, FINGERPRINT, 0, 0, null);
        assertEquals(new BigDecimal("9223372036854775808.00000000"), first.chunkRange().end());
        var cursor = new ChunkRange(
                ORDERS, "bucket", LogicalTypeRoot.DECIMAL, first.chunkRange().end(), null);
        assigner.appendPlan(first, 1);
        var saved = SERIALIZER.deserialize(
                SERIALIZER.getVersion(), SERIALIZER.serialize(assigner.snapshotState(9)));
        var restored = new MySqlHybridSplitAssigner(FINGERPRINT, saved);
        MySqlSnapshotSplit last = planner.planNext(
                ORDERS, FINGERPRINT, restored.nextTableIndex(), restored.nextChunkIndex(),
                restored.nextChunkRange());
        assertEquals(cursor.start(), last.chunkRange().start());
        assertNull(last.chunkRange().end());
        restored.appendPlan(last, 1);
        assertTrue(restored.snapshotState(10).snapshotPlanned());
        assertNull(restored.snapshotState(10).nextChunkRange());
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
        assertThrows(IllegalArgumentException.class, () -> planner.planNext(
                ORDERS, FINGERPRINT, 0, 1,
                new ChunkRange(ORDERS, "id", LogicalTypeRoot.BIGINT, 2L, null)));
        assertThrows(IllegalArgumentException.class, () -> planner.planNext(
                ORDERS, FINGERPRINT, 0, 1,
                new ChunkRange(new TableId("other", null, "orders"), "bucket", LogicalTypeRoot.INTEGER, 2L, null)));
        assertThrows(IllegalArgumentException.class,
                () -> planner.planNext(ORDERS, FINGERPRINT, 0, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> planner.planNext(
                        ORDERS, FINGERPRINT, 0, 0,
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
        enumerator.handleSplitRequest(0);
        enumerator.handleSplitRequest(1);
        enumerator.handleSourceEvent(0, new MySqlLowWatermarkEvent(offset(100)));
        context.runOne();
        var first = (MySqlSnapshotSplit) context.assignments(1).getFirst();
        assertEquals(LogicalTypeRoot.BIGINT, first.chunkRange().type());
        assertEquals(5L, first.chunkRange().end());
        var checkpoint = enumerator.snapshotState(1);
        assertEquals(5L, checkpoint.nextChunkRange().start());
        enumerator.close();
    }

    private static MySqlHybridSourceEnumerator enumerator(
            Coordinator context,
            MySqlCdcSourceConfig config,
            String url,
            MySqlHybridEnumeratorState restored) {
        return new MySqlHybridSourceEnumerator(
                context,
                config,
                FINGERPRINT,
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

    private static MySqlCdcSourceConfig configuration(TableSchema schema, String chunkColumn) {
        var builder = MySqlCdcSource.builder()
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

    private static BinlogOffset offset(long pos) {
        return new BinlogOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", pos));
    }

    private static final class Coordinator implements SplitEnumeratorContext<MySqlHybridSplit> {
        private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
        private final Map<Integer, List<MySqlHybridSplit>> assigned = new java.util.HashMap<>();

        @Override
        public int currentParallelism() {
            return 2;
        }

        @Override
        public Set<Integer> registeredReaders() {
            return Set.of(0, 1);
        }

        @Override
        public void assignSplit(MySqlHybridSplit split, int reader) {
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

        List<MySqlHybridSplit> assignments(int reader) {
            return assigned.getOrDefault(reader, List.of());
        }

        void runOne() {
            assertFalse(actions.isEmpty());
            actions.removeFirst().run();
        }
    }
}
