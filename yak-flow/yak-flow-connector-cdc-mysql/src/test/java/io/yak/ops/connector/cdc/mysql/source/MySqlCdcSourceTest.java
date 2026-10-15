package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/** Ensures one-reader Binlog ownership and strict checkpoint compatibility. */
class MySqlCdcSourceTest {

    @Test
    void assignsOneContinuousSplitAndRestoresItsEnumerationState() throws Exception {
        MySqlCdcSource source = definition();
        assertEquals(Boundedness.CONTINUOUS_UNBOUNDED, source.getBoundedness());
        FakeContext context = new FakeContext();
        var enumerator = source.createEnumerator(context);
        enumerator.start();
        enumerator.handleSplitRequest(0);
        assertEquals(1, context.assignments);
        assertNotNull(context.last);
        enumerator.handleSplitRequest(0);
        assertEquals(1, context.assignments);
        MySqlPendingSplitsState state = enumerator.snapshotState(1L);
        assertTrue(state.splitAssigned());

        FakeContext restoredContext = new FakeContext();
        var restored = source.restoreEnumerator(restoredContext, state);
        restored.start();
        restored.handleSplitRequest(0);
        assertEquals(0, restoredContext.assignments);
        enumerator.close();
        restored.close();
    }

    @Test
    void rejectsChangedSourceConfigurationAndInvalidTables() throws Exception {
        MySqlCdcSource source = definition();
        var state = new MySqlPendingSplitsState("other-checkpoint", true, null);
        assertThrows(IllegalArgumentException.class, () -> source.restoreEnumerator(new FakeContext(), state));
        assertThrows(IllegalArgumentException.class, () -> MySqlCdcSource.builder()
                .hostname("localhost")
                .username("yak")
                .password("secret")
                .serverId(5400)
                .topicPrefix("yak")
                .build());
        assertTrue(source.config().toString().contains("redacted"));
        assertTrue(!source.config().toString().contains("secret"));
    }

    private static MySqlCdcSource definition() {
        return MySqlCdcSource.builder()
                .hostname("localhost")
                .username("yak")
                .password("secret")
                .serverId(5400)
                .topicPrefix("yak-cdc")
                .table(
                        new TableId("shop", null, "orders"),
                        new TableSchema(
                                List.of(
                                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                                        new Column("NAME", LogicalTypes.varchar(50))),
                                List.of("ID")))
                .build();
    }

    private static final class FakeContext implements SplitEnumeratorContext<MySqlBinlogSplit> {
        private int assignments;
        private MySqlBinlogSplit last;

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
            assertEquals(0, subtaskId);
            last = split;
            assignments++;
        }

        @Override
        public void signalNoMoreSplits(int subtaskId) {
            throw new AssertionError("Unbounded Binlog must never signal completed work");
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            throw new AssertionError("Single Binlog split needs no discovery");
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }
    }

    @org.junit.jupiter.api.Test
    void reservesDifferentReplicationConnectionsAndRejectsOverflow() {
        var table = new io.yak.ops.core.data.TableId("shop",null,"orders");
        var schema = new io.yak.ops.core.types.TableSchema(java.util.List.of(
                new io.yak.ops.core.types.Column("id", io.yak.ops.core.types.LogicalTypes.BIGINT.copy(false))),java.util.List.of("id"));
        var config = new MySqlCdcSourceConfig("localhost",3306,"user","password",1001,"topic",java.util.Map.of(table,schema),256);
        var ids = new java.util.HashSet<Integer>();
        ids.add(config.serverId());
        for(int i=0;i<16;i++) org.junit.jupiter.api.Assertions.assertTrue(ids.add(config.snapshotServerId(i)));
        var overflow = new MySqlCdcSourceConfig("localhost",3306,"user","password",Integer.MAX_VALUE,"topic",java.util.Map.of(table,schema),256);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->overflow.snapshotServerId(0));
    }
}
