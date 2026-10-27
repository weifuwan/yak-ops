package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.utils.ChunkUtils;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Chunk selection is table-scoped and never requires the physical primary key to be the split key. */
class MySqlChunkKeySelectionTest {
    private static final TableId ORDERS = new TableId("shop", null, "orders");
    private static final TableId ITEMS = new TableId("shop", null, "items");
    private static final TableSchema ORDERS_SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("created_at", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("id"));
    private static final TableSchema ITEMS_SCHEMA = new TableSchema(
            List.of(
                    new Column("sequence", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(100))),
            List.of("sequence"));

    @Test
    void choosesConfiguredNonPrimaryColumnIndependentOfPrimaryKey() {
        assertEquals("id", ChunkUtils.getChunkKeyColumn(ORDERS_SCHEMA, null).name());
        assertEquals("created_at", ChunkUtils.getChunkKeyColumn(ORDERS_SCHEMA, "created_at").name());
        var keyless = new TableSchema(ORDERS_SCHEMA.columns(), List.of());
        assertEquals("created_at", ChunkUtils.getChunkKeyColumn(keyless, "created_at").name());
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(keyless, null));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(keyless, "missing"));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(keyless, ""));
        var nullable = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT)), List.of("id"));
        assertThrows(IllegalArgumentException.class, () -> ChunkUtils.getChunkKeyColumn(nullable, "id"));
    }

    @Test
    void hybridRuntimeAcceptsTypedNumericAndNonPrimarySplitKeys() {
        assertEquals("id", MySqlChunkSplitter.splitKey(ORDERS_SCHEMA, null));
        assertEquals("id", MySqlChunkSplitter.splitKey(ORDERS_SCHEMA, "id"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MySqlChunkSplitter.splitKey(ORDERS_SCHEMA, "created_at"));
        new MySqlCdcSourceFactory().validateSchema(
                options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "created_at").toReadonlyConfig(),
                ORDERS_SCHEMA);
        var keyless = new TableSchema(ORDERS_SCHEMA.columns(), List.of());
        assertThrows(IllegalArgumentException.class, () -> MySqlChunkSplitter.splitKey(keyless, "created_at"));
    }

    @Test
    void factoryKeepsPerTableSelectionsWithoutRelaxingSharedConnectionSettings() throws Exception {
        var factory = new MySqlCdcSourceFactory();
        var measurements = new TableMetrics();
        var first = new TableSourceFactoryContext(
                options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "id").toReadonlyConfig(),
                ORDERS,
                ORDERS_SCHEMA,
                measurements);
        var second = new TableSourceFactoryContext(
                options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "sequence").toReadonlyConfig(),
                ITEMS,
                ITEMS_SCHEMA,
                measurements);
        var selected = assertInstanceOf(MySqlSource.class, factory.createSource(List.of(first, second)));
        assertEquals(StartupMode.INITIAL, selected.startupMode());
        var source = assertInstanceOf(MySqlHybridCdcSource.class, selected.delegate());

        // A matching Source can restore the same checkpoint without inspecting its private config.
        var original = source.createEnumerator(new CoordinatorContext());
        original.start();
        var checkpoint = original.snapshotState(1L);
        original.close();
        var same = builder().port(3307)
                .table(ORDERS, ORDERS_SCHEMA, "id")
                .table(ITEMS, ITEMS_SCHEMA, "sequence")
                .buildHybrid(1024);
        same.restoreEnumerator(new CoordinatorContext(), checkpoint).close();

        // Dropping one table's explicit key must invalidate the saved definition identity.
        var missingOverride = builder().port(3307)
                .table(ORDERS, ORDERS_SCHEMA, "id")
                .table(ITEMS, ITEMS_SCHEMA)
                .buildHybrid(1024);
        assertThrows(
                IllegalArgumentException.class,
                () -> missingOverride.restoreEnumerator(new CoordinatorContext(), checkpoint));

        var changedConnection = new TableSourceFactoryContext(
                options()
                        .set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3308/shop")
                        .set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "sequence")
                        .toReadonlyConfig(),
                ITEMS,
                ITEMS_SCHEMA,
                measurements);
        assertThrows(IllegalArgumentException.class, () -> factory.createSource(List.of(first, changedConnection)));
    }

    @Test
    void factoryRejectsUnknownOrUnsupportedOverrideBeforeConstructingHybridSource() {
        var factory = new MySqlCdcSourceFactory();
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(new TableSourceFactoryContext(
                        options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "unknown").toReadonlyConfig(),
                        ORDERS,
                        ORDERS_SCHEMA)));
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createSource(new TableSourceFactoryContext(
                        options().set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "name").toReadonlyConfig(),
                        ORDERS,
                        ORDERS_SCHEMA)));
    }

    @Test
    void explicitChunkColumnIsNotImplicitlyAssignedToTheDefault() {
        var implicit = builder().table(ORDERS, ORDERS_SCHEMA).build();
        var explicit = builder().table(ORDERS, ORDERS_SCHEMA, "id").build();
        assertTrue(implicit.config().chunkKeyColumns().isEmpty());
        assertEquals(Map.of(ORDERS, "id"), explicit.config().chunkKeyColumns());

    }

    private static Configuration options() {
        return new Configuration()
                .set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3307/shop")
                .set(JdbcConnectionOptions.USERNAME, "user")
                .set(JdbcConnectionOptions.PASSWORD, "password")
                .set(MySqlCdcSourceOptions.SERVER_ID, 6130)
                .set(MySqlCdcSourceOptions.TOPIC_PREFIX, "chunk-test");
    }

    private static MySqlCdcSourceBuilder builder() {
        return MySqlCdcSource.builder()
                .hostname("localhost")
                .username("user")
                .password("password")
                .serverId(6130)
                .topicPrefix("chunk-test");
    }

    private static final class CoordinatorContext implements SplitEnumeratorContext<MySqlHybridSplit> {
        @Override
        public int currentParallelism() {
            return 1;
        }

        @Override
        public Set<Integer> registeredReaders() {
            return Set.of();
        }

        @Override
        public void assignSplit(MySqlHybridSplit split, int subtaskId) {
            throw new AssertionError("Checkpoint identity test must not assign a split");
        }

        @Override
        public void signalNoMoreSplits(int subtaskId) {
            throw new AssertionError("Hybrid Source must remain unbounded");
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            throw new AssertionError("Checkpoint identity test must not open a JDBC connection");
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }
    }
}
