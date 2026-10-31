package io.yak.ops.connector.cdc.mysql.source.enumerator;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.assigners.AssignerStatus;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.HybridPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.assigners.state.SnapshotPendingSplitsState;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaAssembledEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.LatestFinishedSplitsNumberEvent;
import io.yak.ops.connector.cdc.mysql.source.events.LatestFinishedSplitsNumberRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlStopSnapshotEvent;
import io.yak.ops.connector.cdc.mysql.source.events.TableDiscoveryEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplit;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.core.api.connector.source.ReaderInfo;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.connector.source.SplitsAssignment;
import io.yak.ops.core.api.metrics.Gauge;
import io.yak.ops.core.api.metrics.groups.SplitEnumeratorMetricGroup;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises metadata groups, stale ACK rejection and restored HANDOFF to STREAMING. */
class MySqlBinlogHandoffMetadataTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void restoredHandoffCreatesBinlogOnlyAfterSnapshotCheckpoint() {
        var context = new Context();
        var source = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9320)
                .topicPrefix("handoff-groups")
                .splitMetaGroupSize(2)
                .table(
                        TABLE,
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .buildHybrid(2);
        var enumerator = new MySqlSourceEnumerator(
                StartupOptions.initial(), context, source.config(), 2, handoffState(), new TableMetrics());
        enumerator.start();
        // The restored state came from an already durable checkpoint and the
        // Snapshot Assigner can complete its initial transition immediately.
        assertThat(context.assignedToBinlogReader).hasSize(1);
        assertThat(context.toReader0).noneMatch(MySqlResumeBinlogEvent.class::isInstance);

        enumerator.snapshotState(12);
        enumerator.notifyCheckpointComplete(11);
        enumerator.notifyCheckpointComplete(12);
        assertThat(context.assignedToBinlogReader).hasSize(1);
        var assigned = (io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit)
                context.assignedToBinlogReader.getFirst();
        assertThat(assigned.offset()).isEqualTo(offset(100));
        assertThat(assigned.finishedSplits()).isEmpty();
        assertThat(assigned.getTotalFinishedSplitSize()).isEqualTo(3);
        assertThat(assigned.isCompletedSplit()).isFalse();
        assertThat(assigned.schemaHistory()).containsExactly(1, 2, 3);
        // The Reader fetches the same bounded groups as Flink CDC 3.7.
        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(0, 0));
        BinlogSplitMetaEvent first = (BinlogSplitMetaEvent) context.toReader0.getLast();
        assertThat(first.getMetaGroupId()).isZero();
        assertThat(first.getTotalGroups()).isEqualTo(2);
        assertThat(first.getMetaGroup()).hasSize(2);
        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(1, 0));
        BinlogSplitMetaEvent second = (BinlogSplitMetaEvent) context.toReader0.getLast();
        assertThat(second.getMetaGroupId()).isEqualTo(1);
        assertThat(second.getMetaGroup()).hasSize(1);
        enumerator.handleSourceEvent(0, new BinlogSplitMetaAssembledEvent(0));
        assertThat(context.toReader0).noneMatch(MySqlResumeBinlogEvent.class::isInstance);
        enumerator.close();
    }

    @Test
    void acceptsSnapshotMetadataReleaseWhenReleasedStateIsCheckpointed() {
        var source = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9321)
                .topicPrefix("handoff-release")
                .releaseSnapshotMetadataEnabled(true)
                .table(
                        TABLE,
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .buildHybrid(2);
        assertThatCode(() -> new MySqlSourceEnumerator(
                        StartupOptions.initial(), new Context(), source.config(), 2, handoffState(), new TableMetrics()))
                .doesNotThrowAnyException();
    }

    @Test
    void restoredNewTableSnapshotRequestsCurrentBinlogMetadataAndAcknowledgesUpdate() {
        var context = new Context();
        var source = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9322)
                .topicPrefix("new-table-handoff")
                .scanNewlyAddedTableEnabled(true)
                .table(
                        TABLE,
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .buildHybrid(2);
        var finished = new SnapshotPendingSplitsState(
                true,
                3,
                List.of(),
                Map.of(),
                Set.of("chunk-0", "chunk-1", "chunk-2"),
                List.of(info("chunk-0", 100), info("chunk-1", 200), info("chunk-2", 300)),
                1,
                0,
                null,
                List.of(TABLE),
                List.of(),
                AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED,
                false,
                true,
                false);
        var restored = new HybridPendingSplitsState(
                finished, true, null, HybridPendingSplitsState.Phase.STREAMING, offset(50), offset(300));
        var enumerator = new MySqlSourceEnumerator(
                StartupOptions.initial(), context, source.config(), 2, restored, new TableMetrics());
        enumerator.start();

        assertThat(context.periodicDiscovery).isNotNull();
        TableId anotherTable = new TableId("shop", null, "new_orders");
        Schema anotherSchema = source.config().physicalSchemas().get(TABLE);
        context.completeDiscovery(Map.of(anotherTable, anotherSchema));
        assertThat(context.toReader0).noneMatch(TableDiscoveryEvent.class::isInstance);

        var update = (BinlogSplitUpdateRequestEvent) context.toReader0.getFirst();
        assertThat(update.getGeneration()).isPositive();
        enumerator.addReader(0);
        assertThat(((BinlogSplitUpdateRequestEvent) context.toReader0.getLast()).getGeneration())
                .isEqualTo(update.getGeneration());

        enumerator.handleSourceEvent(0, new LatestFinishedSplitsNumberRequestEvent(2));
        var current = (LatestFinishedSplitsNumberEvent) context.toReader0.getLast();
        assertThat(current.getLatestFinishedSplitsNumber()).isEqualTo(3);
        assertThat(current.getBinlogAssignmentGeneration()).isEqualTo(update.getGeneration());
        assertThat(current.isSnapshotFinished()).isTrue();

        assertThatThrownBy(() -> enumerator.handleSourceEvent(
                        1, new LatestFinishedSplitsNumberRequestEvent(2)))
                .isInstanceOf(IllegalArgumentException.class);
        enumerator.handleSourceEvent(0, new LatestFinishedSplitsNumberRequestEvent(4));
        var reconciled = (LatestFinishedSplitsNumberEvent) context.toReader0.getLast();
        assertThat(reconciled.getLatestFinishedSplitsNumber()).isEqualTo(3);

        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(0, update.getGeneration() - 1));
        assertThat(context.toReader0.getLast()).isSameAs(reconciled);
        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(0, update.getGeneration()));
        assertThat(context.toReader0.getLast()).isInstanceOf(BinlogSplitMetaEvent.class);
        assertThat(((BinlogSplitMetaEvent) context.toReader0.getLast()).getTotalFinishedSplitSize())
                .isEqualTo(3);

        enumerator.handleSourceEvent(0, new BinlogSplitMetaAssembledEvent(update.getGeneration()));
        assertThat(context.toReader0.getLast()).isInstanceOf(MySqlResumeBinlogEvent.class);
        enumerator.handleSourceEvent(0, new BinlogSplitUpdateAckEvent(update.getGeneration() - 1));
        assertThat(((HybridPendingSplitsState) enumerator.snapshotState(1))
                        .getSnapshotPendingSplits().getSnapshotAssignerStatus())
                .isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING_SNAPSHOT_FINISHED);

        enumerator.handleSourceEvent(0, new BinlogSplitUpdateAckEvent(update.getGeneration()));
        enumerator.handleSourceEvent(0, new BinlogSplitUpdateAckEvent(update.getGeneration()));
        assertThat(((HybridPendingSplitsState) enumerator.snapshotState(2))
                        .getSnapshotPendingSplits().getSnapshotAssignerStatus())
                .isEqualTo(AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED);
        context.completeDiscovery(Map.of(anotherTable, anotherSchema));
        assertThat(context.toReader0.getLast()).isInstanceOf(TableDiscoveryEvent.class);
        assertThat(((TableDiscoveryEvent) context.toReader0.getLast()).table()).isEqualTo(anotherTable);
        enumerator.close();
    }

    @Test
    void restoredCompletedAssignmentDefersNextTableUntilCurrentMetadataUpdateIsAcknowledged() {
        var context = new Context();
        var source = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9323)
                .topicPrefix("restored-completed-handoff")
                .scanNewlyAddedTableEnabled(true)
                .splitMetaGroupSize(2)
                .table(TABLE, new TableSchema(
                        List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id")))
                .buildHybrid(2);
        var snapshots = new SnapshotPendingSplitsState(
                true,
                3,
                List.of(),
                Map.of(),
                Set.of("chunk-0", "chunk-1", "chunk-2"),
                List.of(info("chunk-0", 100), info("chunk-1", 200), info("chunk-2", 300)),
                1,
                0,
                null,
                List.of(TABLE),
                List.of(),
                AssignerStatus.NEWLY_ADDED_ASSIGNING_FINISHED,
                false,
                true,
                false);
        var restored = new HybridPendingSplitsState(
                snapshots, true, null, HybridPendingSplitsState.Phase.STREAMING, offset(50), offset(300));
        var enumerator = new MySqlSourceEnumerator(
                StartupOptions.initial(), context, source.config(), 2, restored, new TableMetrics());
        enumerator.start();
        var update = (BinlogSplitUpdateRequestEvent) context.toReader0.getFirst();
        long generation = update.getGeneration();
        TableId anotherTable = new TableId("shop", null, "new_orders");
        Map<TableId, Schema> discovered = Map.of(anotherTable, source.config().physicalSchemas().get(TABLE));

        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(0, generation));
        BinlogSplitMetaEvent first = (BinlogSplitMetaEvent) context.toReader0.getLast();
        assertThat(first.getTotalGroups()).isEqualTo(2);
        assertThat(first.getTotalFinishedSplitSize()).isEqualTo(3);
        context.completeDiscovery(discovered);
        assertThat(context.toReader0).noneMatch(TableDiscoveryEvent.class::isInstance);
        assertThat(context.toReader1).noneMatch(TableDiscoveryEvent.class::isInstance);

        enumerator.handleSourceEvent(0, new BinlogSplitMetaRequestEvent(1, generation));
        BinlogSplitMetaEvent second = (BinlogSplitMetaEvent) context.toReader0.getLast();
        assertThat(second.getTotalGroups()).isEqualTo(first.getTotalGroups());
        assertThat(second.getTotalFinishedSplitSize()).isEqualTo(first.getTotalFinishedSplitSize());
        enumerator.handleSourceEvent(0, new BinlogSplitMetaAssembledEvent(generation));
        assertThat(context.toReader0.getLast()).isInstanceOf(MySqlResumeBinlogEvent.class);
        enumerator.handleSourceEvent(0, new BinlogSplitUpdateAckEvent(generation - 1));
        context.completeDiscovery(discovered);
        assertThat(context.toReader0).noneMatch(TableDiscoveryEvent.class::isInstance);

        enumerator.handleSourceEvent(0, new BinlogSplitUpdateAckEvent(generation));
        context.completeDiscovery(discovered);
        assertThat(context.toReader0.getLast()).isInstanceOf(TableDiscoveryEvent.class);
        assertThat(((TableDiscoveryEvent) context.toReader0.getLast()).table()).isEqualTo(anotherTable);
        assertThat(context.toReader1.getLast()).isSameAs(context.toReader0.getLast());
        enumerator.close();
    }

    private static HybridPendingSplitsState handoffState() {
        List<FinishedSnapshotSplitInfo> infos = List.of(
                info("chunk-0", 100), info("chunk-1", 200), info("chunk-2", 300));
        var snapshots = new SnapshotPendingSplitsState(
                true, 3, List.of(), Map.of(), Set.of("chunk-0", "chunk-1", "chunk-2"), infos, 1, 0, null);
        return new HybridPendingSplitsState(
                snapshots, false, null, HybridPendingSplitsState.Phase.HANDOFF, null, offset(300),
                offset(100), new byte[] {1, 2, 3});
    }

    private static FinishedSnapshotSplitInfo info(String id, long position) {
        return new FinishedSnapshotSplitInfo(TABLE, id, null, null, offset(position));
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(
                Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static final class Context implements SplitEnumeratorContext<MySqlSplit> {
        private final List<SourceEvent> toReader0 = new ArrayList<>();
        private final List<SourceEvent> toReader1 = new ArrayList<>();
        private final List<MySqlSplit> assignedToBinlogReader = new ArrayList<>();
        private BiConsumer<Map<TableId, Schema>, Throwable> periodicDiscovery;

        private void completeDiscovery(Map<TableId, Schema> tables) {
            periodicDiscovery.accept(tables, null);
        }

        @Override
        public int currentParallelism() {
            return 2;
        }

        @Override
        public Map<Integer, ReaderInfo> registeredReaders() {
            return Map.of(0, new ReaderInfo(0, ""), 1, new ReaderInfo(1, ""));
        }

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
        public void assignSplit(MySqlSplit split, int reader) {
            if (reader == 0) assignedToBinlogReader.add(split);
        }

        @Override
        public void signalNoMoreSplits(int reader) {}

        @Override
        public void sendEventToSourceReader(int reader, SourceEvent event) {
            if (reader == 0) toReader0.add(event);
            else if (reader == 1) toReader1.add(event);
        }

        @Override
        public void runInCoordinatorThread(Runnable action) {
            action.run();
        }

        @Override
        public <T> void callAsync(Callable<T> action, BiConsumer<T, Throwable> handler) {
            throw new AssertionError("Completed handoff must not rediscover Snapshot chunks");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void callAsync(
                Callable<T> action,
                BiConsumer<T, Throwable> handler,
                long initialDelayMillis,
                long periodMillis) {
            assertThat(periodicDiscovery).isNull();
            assertThat(initialDelayMillis).isPositive();
            assertThat(periodMillis).isPositive();
            periodicDiscovery = (result, failure) -> handler.accept((T) result, failure);
        }
    }
}
