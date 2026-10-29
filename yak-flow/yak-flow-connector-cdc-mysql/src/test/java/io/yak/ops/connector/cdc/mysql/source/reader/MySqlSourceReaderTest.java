package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaAssembledEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.LatestFinishedSplitsNumberEvent;
import io.yak.ops.connector.cdc.mysql.source.events.LatestFinishedSplitsNumberRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlLowWatermarkEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsRequestEvent;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * SourceReader control-plane contract tests following Flink CDC's
 * MySqlSourceReaderTest. The reader handles Flink-style bounded Binlog metadata
 * groups and finished Snapshot split reporting/ACK separately.
 */
class MySqlSourceReaderTest {

    @Test
    void testStartRequestsSplitFromEnumeratorExactlyOncePerStart() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(requests), new TableMetrics(), false)) {
            reader.start();
            assertThat(requests.get()).isEqualTo(1);
            assertThat(reader.snapshotState(1)).isEmpty();
            reader.notifyCheckpointComplete(1);
        }
    }

    @Test
    void testBinlogOnlyReaderRejectsHybridCoordinatorEvents() throws Exception {
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger()), new TableMetrics(), false)) {
            assertThatThrownBy(() -> reader.handleSourceEvents(new FinishedSnapshotSplitsRequestEvent()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> reader.handleSourceEvents(
                    new FinishedSnapshotSplitsAckEvent(List.of("chunk-0"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testSnapshotReaderRequestWithoutFinishedSplitsIsNoOp() throws Exception {
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger()), new TableMetrics(), true)) {
            reader.handleSourceEvents(new FinishedSnapshotSplitsRequestEvent());
            reader.handleSourceEvents(new FinishedSnapshotSplitsAckEvent(List.of("missing")));
            assertThat(reader.snapshotState(2)).isEmpty();
            reader.notifyCheckpointComplete(2);
        }
    }

    @Test
    void restoredFinishedSnapshotRereportsOffsetAndDebeziumHistoryWithoutRescanning() throws Exception {
        var events = new ArrayList<SourceEvent>();
        var table = new TableId("shop", null, "orders");
        var high = offset(150);
        var finished = new FinishedSnapshotSplitInfo(table, "orders-0", null, null, high);
        var restored = new io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit(
                "orders-0", table, null, null, null, null, 0, finished, null, new byte[] {1, 2, 3});
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger(), events), new TableMetrics(), true)) {
            reader.addSplits(List.of(restored));
            assertThat(events).hasSize(1);
            var report = (io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsReportEvent)
                    events.getFirst();
            assertThat(report.getFinishedOffsets()).containsEntry("orders-0", high);
            assertThat(report.getFinishedSchemaHistories().get("orders-0")).containsExactly(1, 2, 3);
            assertThat(reader.snapshotState(5)).containsExactly(restored);
            reader.handleSourceEvents(new FinishedSnapshotSplitsRequestEvent());
            assertThat(events).hasSize(2);
            var rereported = (io.yak.ops.connector.cdc.mysql.source.events.FinishedSnapshotSplitsReportEvent)
                    events.getLast();
            assertThat(rereported.getFinishedSchemaHistories().get("orders-0"))
                    .containsExactly(1, 2, 3);
        }
    }

    @Test
    void checkpointRestoredPausedBinlogRereportsLowWatermarkToCoordinator() throws Exception {
        var events = new ArrayList<SourceEvent>();
        var requests = new AtomicInteger();
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(requests, events), new TableMetrics(), true)) {
            reader.addSplits(List.of(MySqlBinlogSplit.forPausedSnapshot(offset(120), new byte[] {1})));
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(MySqlLowWatermarkEvent.class);
            assertThat(((MySqlLowWatermarkEvent) events.getFirst()).offset()).isEqualTo(offset(120));
            assertThat(requests.get()).isEqualTo(1);
        }
    }

    @Test
    void incompleteInitialBinlogRemainsCheckpointedUntilAllMetadataGroupsArrive() throws Exception {
        var events = new ArrayList<SourceEvent>();
        var table = new TableId("shop", null, "orders");
        var first = new FinishedSnapshotSplitInfo(table, "chunk-0", null, null, offset(100));
        var pending = MySqlBinlogSplit.forCompletedSnapshot(
                offset(100), new byte[] {1}, offset(200), List.of(), 2);
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger(), events), new TableMetrics(), true)) {
            reader.addSplits(List.of(pending));
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(BinlogSplitMetaRequestEvent.class);
            assertThat(((BinlogSplitMetaRequestEvent) events.getFirst()).getGroupId()).isZero();
            assertThat(reader.snapshotState(1)).containsExactly(pending);
            reader.handleSourceEvents(new BinlogSplitMetaEvent(
                    0, 2, 2, 0, List.of(FinishedSnapshotSplitInfo.serialize(first))));
            assertThat(events.getLast()).isInstanceOf(BinlogSplitMetaRequestEvent.class);
            assertThat(((BinlogSplitMetaRequestEvent) events.getLast()).getGroupId()).isEqualTo(1);
            // Uncheckpointed assembly progress is transient; the original incomplete split
            // must be recoverable even if the reader crashes between metadata groups.
            assertThat(reader.snapshotState(2)).containsExactly(pending);
            assertThat(events).noneMatch(BinlogSplitMetaAssembledEvent.class::isInstance);
        }
    }

    @Test
    void assemblesSerializedMetadataGroupsBeforeResumingBinlog() throws Exception {
        var events = new ArrayList<SourceEvent>();
        var table = new TableId("shop", null, "orders");
        var first = new FinishedSnapshotSplitInfo(table, "chunk-0", null, null, offset(100));
        var second = new FinishedSnapshotSplitInfo(table, "chunk-1", null, null, offset(200));
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger(), events), new TableMetrics(), true)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> reader.handleSourceEvents(new MySqlResumeBinlogEvent(offset(200))))
                    .isInstanceOf(IllegalStateException.class);
            reader.handleSourceEvents(new BinlogSplitMetaEvent(
                    0, 2, 2, 1, List.of(FinishedSnapshotSplitInfo.serialize(first))));
            assertThat(events.getFirst()).isInstanceOf(BinlogSplitMetaRequestEvent.class);
            assertThat(((BinlogSplitMetaRequestEvent) events.getFirst()).getGroupId()).isEqualTo(1);
            reader.handleSourceEvents(new BinlogSplitMetaEvent(
                    1, 2, 2, 1, List.of(FinishedSnapshotSplitInfo.serialize(second))));
            assertThat(events.getLast()).isInstanceOf(BinlogSplitMetaAssembledEvent.class);
            assertThat(((BinlogSplitMetaAssembledEvent) events.getLast()).getBinlogAssignmentGeneration())
                    .isEqualTo(1);
            reader.handleSourceEvents(new MySqlResumeBinlogEvent(offset(200)));
            assertThat(reader.snapshotState(3)).isEmpty();
        }
    }

    @Test
    void restoredSuspendedBinlogRequestsLatestSnapshotCountWithoutActivatingDuplicateSplit() throws Exception {
        var events = new ArrayList<SourceEvent>();
        var requests = new AtomicInteger();
        var table = new TableId("shop", null, "orders");
        var completed = new FinishedSnapshotSplitInfo(table, "chunk-0", null, null, offset(100));
        var running = MySqlBinlogSplit.forInitialSnapshot()
                .withAnchor(offset(50), new byte[] {1})
                .startStreaming(offset(100), List.of(completed))
                .withEmittedOffset(offset(150), new byte[] {1});
        var paused = MySqlBinlogSplit.toSuspendedBinlogSplit(running);

        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(requests, events), new TableMetrics(), true)) {
            reader.addSplits(List.of(paused));
            assertThat(reader.snapshotState(1)).containsExactly(paused);
            assertThat(events).hasSize(1);
            assertThat(events.getFirst()).isInstanceOf(LatestFinishedSplitsNumberRequestEvent.class);
            assertThat(((LatestFinishedSplitsNumberRequestEvent) events.getFirst())
                            .getRestoredFinishedSplitsNumber())
                    .isEqualTo(1);
            assertThat(requests.get()).isEqualTo(1);

            reader.handleSourceEvents(new LatestFinishedSplitsNumberEvent(2, 17, false));
            assertThat(events.getLast()).isInstanceOf(BinlogSplitMetaRequestEvent.class);
            assertThat(((BinlogSplitMetaRequestEvent) events.getLast()).getGeneration()).isEqualTo(17);
            assertThat(reader.snapshotState(2)).containsExactly(paused);

            // The previous attempt's update request must never replace a newer generation.
            int eventsBeforeStaleRequest = events.size();
            reader.handleSourceEvents(new BinlogSplitUpdateRequestEvent(16));
            assertThat(events).hasSize(eventsBeforeStaleRequest);
        }
    }

    @Test
    void restoredSuspendedBinlogFailsClosedWhenCoordinatorHasLostFinishedFences() throws Exception {
        var table = new TableId("shop", null, "orders");
        var completed = new FinishedSnapshotSplitInfo(table, "chunk-0", null, null, offset(100));
        var paused = MySqlBinlogSplit.toSuspendedBinlogSplit(
                MySqlBinlogSplit.forInitialSnapshot()
                        .withAnchor(offset(50), new byte[] {1})
                        .startStreaming(offset(100), List.of(completed)));
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger()), new TableMetrics(), true)) {
            reader.addSplits(List.of(paused));
            assertThatThrownBy(
                            () -> reader.handleSourceEvents(new LatestFinishedSplitsNumberEvent(0, 1, true)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("without completed Snapshot fences");
        }
    }

    @Test
    void restoresPausedBinlogWhenCoordinatorHasRemovedAnOldCapturedTable() throws Exception {
        var table = new TableId("shop", null, "orders");
        var oldTable = new TableId("shop", null, "old_orders");
        var completed = List.of(
                new FinishedSnapshotSplitInfo(table, "current-0", null, null, offset(100)),
                new FinishedSnapshotSplitInfo(oldTable, "removed-0", null, null, offset(100)));
        var paused = MySqlBinlogSplit.toSuspendedBinlogSplit(
                MySqlBinlogSplit.forInitialSnapshot()
                        .withAnchor(offset(50), new byte[] {1})
                        .startStreaming(offset(100), completed));
        var events = new ArrayList<SourceEvent>();
        try (MySqlSourceReader reader = new MySqlSourceReader(
                config(), context(new AtomicInteger(), events), new TableMetrics(), true)) {
            reader.addSplits(List.of(paused));
            assertThat(((LatestFinishedSplitsNumberRequestEvent) events.getFirst())
                            .getRestoredFinishedSplitsNumber())
                    .isEqualTo(2);
            reader.handleSourceEvents(new LatestFinishedSplitsNumberEvent(1, 7, true));
            assertThat(events.getLast()).isInstanceOf(BinlogSplitMetaRequestEvent.class);
            assertThat(((BinlogSplitMetaRequestEvent) events.getLast()).getGeneration()).isEqualTo(7);
            assertThat(reader.snapshotState(2)).containsExactly(paused);
        }
    }

    private static DebeziumOffset offset(long position) {
        return new DebeziumOffset(Map.of("server", "shop"), Map.of("file", "mysql-bin.000001", "pos", position));
    }

    private static io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig config() {
        return MySqlSource.builder()
                .hostname("localhost")
                .username("reader")
                .password("secret")
                .serverId(9320)
                .topicPrefix("reader-test")
                .table(
                        new TableId("shop", null, "orders"),
                        new TableSchema(
                                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                                List.of("id")))
                .build()
                .config();
    }

    private static SourceReaderContext context(AtomicInteger requests) {
        return context(requests, new ArrayList<>());
    }

    private static SourceReaderContext context(AtomicInteger requests, List<SourceEvent> events) {
        return new SourceReaderContext() {
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
                requests.incrementAndGet();
            }
            @Override
            public void sendSourceEventToCoordinator(SourceEvent event) {
                events.add(event);
            }
        };
    }
}
