package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.utils.MySqlChangeEventIds;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotOutput;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.connector.cdc.mysql.debezium.DebeziumOffset;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.ChangeEventId;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Stable Debezium coordinates and atomic UPDATE identity across Source emission. */
class MySqlRecordEmitterTest {
    private static final TableId TABLE = new TableId("shop", null, "entries");

    @Test
    void originalMutationSelectionPreservesSnapshotsAndAdvancesRejectedBinlogOffsets() throws Exception {
        TableSchema schema = new TableSchema(List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = MySqlSource.builder().hostname("localhost").username("user").password("secret")
                .serverId(9001).topicPrefix("selection-test").table(TABLE, schema)
                .tableOptions(TABLE, new io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions(false, null, false, true, false))
                .build().config();
        try (MySqlSplitReader reader = new MySqlSplitReader(config)) {
            MySqlRecordEmitter emitter = new MySqlRecordEmitter(new MySqlSourceReaderContext(context()), reader,
                    new TableMetrics(), "selection-test");
            MySqlSplitState state = MySqlSplitState.fromSplit(new MySqlBinlogSplit(null, new byte[0]));
            List<TableRecord> output = new ArrayList<>();
            emitter.emitRecord(MySqlFetchedRecord.binlog(new BinlogEvent(List.of(
                    new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L))), position(20, 1))), output::add, state);
            emitter.emitRecord(MySqlFetchedRecord.binlog(new BinlogEvent(List.of(
                    new TableRecord(TABLE, RowKind.UPDATE_BEFORE, GenericRowData.of(1L)),
                    new TableRecord(TABLE, RowKind.UPDATE_AFTER, GenericRowData.of(2L))), position(30, 1))), output::add, state);
            emitter.emitRecord(MySqlFetchedRecord.binlog(new BinlogEvent(List.of(
                    new TableRecord(TABLE, RowKind.DELETE, GenericRowData.of(2L))), position(40, 1))), output::add, state);
            assertEquals(List.of(RowKind.UPDATE_BEFORE, RowKind.UPDATE_AFTER), output.stream().map(TableRecord::rowKind).toList());
            assertEquals(position(40, 1), ((MySqlBinlogSplit) state.checkpoint(new byte[] {1})).offset());

            MySqlSnapshotSplit split = new MySqlSnapshotSplit("filtered-snapshot", TABLE, null, null, null);
            MySqlSnapshotSplit prepared = split.withOutput(new MySqlSnapshotOutput(position(10, 1), position(15, 1),
                    List.of(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(7L))), new byte[] {1}));
            emitter.emitRecord(MySqlFetchedRecord.prepared(prepared), output::add, MySqlSplitState.fromSplit(split));
            assertEquals(7L, output.getLast().row().getLong(0));
            assertEquals(RowKind.INSERT, output.getLast().rowKind());
        }
    }

    @Test
    void reorderingOffsetMapsOrIntegralNumberTypesNeverChangesIdentity() {
        var partition = new LinkedHashMap<String, Object>();
        partition.put("server", "yak");
        partition.put("connector", "mysql");
        var position = new LinkedHashMap<String, Object>();
        position.put("file", "mysql-bin.000004");
        position.put("pos", 600L);
        position.put("event", 3);
        position.put("row", 1L);
        position.put("gtids", "abc:1-2");
        var first = new DebeziumOffset(partition, position);
        var reversed = new DebeziumOffset(
                Map.of("connector", "mysql", "server", "yak"),
                Map.of("gtids", "abc:1-2", "row", 1, "event", 3L,
                        "pos", 600, "file", "mysql-bin.000004"));
        assertEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(reversed));
        assertNotEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(partition, Map.of("file", "mysql-bin.000004",
                        "pos", 600, "event", 3, "row", 2))));
        assertNotEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(partition, Map.of("file", "mysql-bin.000005",
                        "pos", 600, "event", 3, "row", 1))));
        assertNotEquals(MySqlChangeEventIds.fromBinlog(first), MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "other"), position)));
    }


    @Test
    void eventIdentityRemainsStableAfterSplitSerialization() throws Exception {
        DebeziumOffset offset = position(88, 2);
        BinlogEvent event = new BinlogEvent(List.of(row(RowKind.INSERT, "payload")), offset);
        var reader = binlogEmitter();
        var first = MySqlSplitState.fromSplit(new MySqlBinlogSplit(null, new byte[0]));
        var serializer = MySqlSplitSerializer.INSTANCE;
        var second = MySqlSplitState.fromSplit(serializer.deserialize(
                serializer.getVersion(), serializer.serialize(new MySqlBinlogSplit(null, new byte[0]))));
        List<TableRecord> earlier = new ArrayList<>();
        List<TableRecord> later = new ArrayList<>();
        reader.emitRecord(MySqlFetchedRecord.binlog(event), earlier::add, first);
        reader.emitRecord(MySqlFetchedRecord.binlog(event), later::add, second);
        assertEquals(earlier.getFirst().changeEventId(), later.getFirst().changeEventId());
        assertEquals("emission-test", earlier.getFirst().changeEventId().sourceId());
    }

    @Test
    void incompleteOffsetsCannotBePresentedAsReplaySafeIdentities() {
        assertNull(MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.1", "pos", 42))));
        assertNull(MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "yak"),
                        Map.of("file", "mysql-bin.1", "pos", 42, "event", 1))));
        assertNull(MySqlChangeEventIds.fromBinlog(
                new DebeziumOffset(Map.of("server", "yak"), Map.of("gtids", "abc:1", "event", 1, "row", 1))));
    }

    @Test
    void updateImagesShareEventIdentityButKeepDifferentImageIndexesAndCheckpointOrder() throws Exception {
        DebeziumOffset position = position(44, 3);
        BinlogEvent update = new BinlogEvent(
                List.of(row(RowKind.UPDATE_BEFORE, "same"), row(RowKind.UPDATE_AFTER, "same")),
                position);
        var state = MySqlSplitState.fromSplit(new MySqlBinlogSplit(null, new byte[0]));
        var emitter = binlogEmitter();
        List<TableRecord> delivered = new ArrayList<>();
        emitter.emitRecord(MySqlFetchedRecord.binlog(update), delivered::add, state);
        assertEquals(2, delivered.size());
        ChangeEventId before = delivered.getFirst().changeEventId();
        ChangeEventId after = delivered.getLast().changeEventId();
        assertTrue(before.sameEvent(after));
        assertEquals(0, before.imageIndex());
        assertEquals(1, after.imageIndex());
        assertEquals(2, before.imageCount());
        assertEquals(2, after.imageCount());
        assertEquals(position, ((MySqlBinlogSplit) state.checkpoint(new byte[] {1})).offset());

        var restored = MySqlSplitState.fromSplit(
                new MySqlBinlogSplit(position(42, 1), new byte[] {1}));
        List<TableRecord> replayed = new ArrayList<>();
        emitter.emitRecord(MySqlFetchedRecord.binlog(update), replayed::add, restored);
        assertEquals(before, replayed.getFirst().changeEventId());
        assertEquals(after, replayed.getLast().changeEventId());
        assertEquals(position, ((MySqlBinlogSplit) restored.checkpoint(new byte[] {1})).offset());
        assertEquals("same", replayed.getFirst().row().getString(0));

        var failed = MySqlSplitState.fromSplit(
                new MySqlBinlogSplit(position(42, 1), new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> emitter.emitRecord(MySqlFetchedRecord.binlog(update), record -> {
            if (record.rowKind() == RowKind.UPDATE_AFTER) throw new IllegalStateException("sink failed");
        }, failed));
        assertEquals(position(42, 1), ((MySqlBinlogSplit) failed.checkpoint(new byte[] {1})).offset());
    }


    @Test
    void atomicSnapshotEmissionKeepsStableOrdinalsAndMetadataOnlyCheckpoint() throws Exception {
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost").username("user").password("secret")
                .serverId(9001).topicPrefix("identity-test").table(TABLE, schema)
                .build().config();
        try (var splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(
                    new MySqlSourceReaderContext(context()), splitReader, new TableMetrics(), config.topicPrefix());
            var rows = List.of(
                    new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L)),
                    new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L)));
            var raw = new MySqlSnapshotSplit("chunk-0", TABLE, null, null, null);
            var prepared = raw.withOutput(new MySqlSnapshotOutput(position(10, 0), position(20, 0), rows));
            var state = MySqlSplitState.fromSplit(raw);
            var output = new ArrayList<TableRecord>();
            emitter.emitRecord(MySqlFetchedRecord.prepared(prepared), output::add, state);
            assertEquals(2, output.size());
            assertNotEquals(output.getFirst().changeEventId(), output.getLast().changeEventId());
            assertEquals("identity-test", output.getFirst().changeEventId().sourceId());
            assertTrue(output.getFirst().changeEventId().eventId().startsWith("snapshot:chunk-0:key:"));
            // The deliberately duplicated physical key is not valid MySQL table data,
            // but distinct emitted images must not reuse one event identity.
            assertTrue(output.getFirst().changeEventId().eventId().endsWith(":0"));
            assertTrue(output.getLast().changeEventId().eventId().endsWith(":1"));
            var serializer = new MySqlSplitSerializer();
            var restored = (MySqlSnapshotSplit) serializer.deserialize(
                    serializer.getVersion(), serializer.serialize((MySqlSnapshotSplit) state.checkpoint(null)));
            assertEquals(null, restored.output());
            assertEquals(position(20, 0), restored.finished().highWatermark());
            assertEquals(null, ((MySqlSnapshotSplit) MySqlSplitState.fromSplit(raw).checkpoint(null)).finished());
        }
    }


    @Test
    void snapshotIdentitiesFollowPhysicalKeysWhenReplayedChunkChangesOrder() throws Exception {
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(100))),
                List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost").username("user").password("secret")
                .serverId(9030).topicPrefix("keyed-identity-test").table(TABLE, schema)
                .build().config();
        try (var splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(
                    new MySqlSourceReaderContext(context()), splitReader, new TableMetrics(), config.topicPrefix());
            var raw = new MySqlSnapshotSplit("stable-chunk", TABLE, null, null, null);
            var first = raw.withOutput(new MySqlSnapshotOutput(
                    position(10, 0), position(20, 0),
                    List.of(
                            new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "before")),
                            new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L, "second")))));
            var retried = raw.withOutput(new MySqlSnapshotOutput(
                    position(10, 0), position(30, 0),
                    List.of(
                            new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(0L, "new")),
                            new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L, "updated")),
                            new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "after")))));
            var originalRows = new ArrayList<TableRecord>();
            var retriedRows = new ArrayList<TableRecord>();
            emitter.emitRecord(MySqlFetchedRecord.prepared(first), originalRows::add, MySqlSplitState.fromSplit(raw));
            emitter.emitRecord(MySqlFetchedRecord.prepared(retried), retriedRows::add, MySqlSplitState.fromSplit(raw));

            assertEquals(originalRows.get(0).changeEventId(), retriedRows.get(2).changeEventId());
            assertEquals(originalRows.get(1).changeEventId(), retriedRows.get(1).changeEventId());
            assertNotEquals(originalRows.get(0).changeEventId(), retriedRows.get(0).changeEventId());
            assertNotEquals(retriedRows.get(0).changeEventId(), retriedRows.get(1).changeEventId());
        }
    }

    @Test
    void keylessSnapshotIdentitiesPreserveDuplicateOccurrenceAndIgnoreOtherRowInsertions() throws Exception {
        var schema = new TableSchema(
                List.of(new Column("name", LogicalTypes.varchar(100).copy(false))), List.of());
        var config = new io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig(
                "localhost", 3306, "user", "secret", 9031, "keyless-identity-test",
                Map.of(TABLE, schema), 128, Map.of(), Map.of(TABLE, "name"));
        try (var splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(
                    new MySqlSourceReaderContext(context()), splitReader, new TableMetrics(), config.topicPrefix());
            var raw = new MySqlSnapshotSplit("keyless-chunk", TABLE, null, null, null);
            var original = raw.withOutput(new MySqlSnapshotOutput(
                    position(10, 0), position(20, 0), List.of(
                            row(RowKind.INSERT, "duplicate"), row(RowKind.INSERT, "duplicate"))));
            var retried = raw.withOutput(new MySqlSnapshotOutput(
                    position(10, 0), position(25, 0), List.of(
                            row(RowKind.INSERT, "other"), row(RowKind.INSERT, "duplicate"),
                            row(RowKind.INSERT, "duplicate"))));
            var before = new ArrayList<TableRecord>();
            var after = new ArrayList<TableRecord>();
            emitter.emitRecord(MySqlFetchedRecord.prepared(original), before::add, MySqlSplitState.fromSplit(raw));
            emitter.emitRecord(MySqlFetchedRecord.prepared(retried), after::add, MySqlSplitState.fromSplit(raw));
            assertNotEquals(before.get(0).changeEventId(), before.get(1).changeEventId());
            assertEquals(before.get(0).changeEventId(), after.get(1).changeEventId());
            assertEquals(before.get(1).changeEventId(), after.get(2).changeEventId());
        }
    }

    @Test
    void interruptedSnapshotChunkDoesNotPublishFinishedWatermark() throws Exception {
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost").username("user").password("secret")
                .serverId(9001).topicPrefix("atomic-snapshot-test").table(TABLE, schema)
                .build().config();
        try (var splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(
                    new MySqlSourceReaderContext(context()), splitReader, new TableMetrics(), config.topicPrefix());
            var rows = List.of(
                    new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L)),
                    new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L)));
            var raw = new MySqlSnapshotSplit("chunk-atomic", TABLE, null, null, null);
            var prepared = raw.withOutput(new MySqlSnapshotOutput(position(10, 0), position(20, 0), rows));
            var state = MySqlSplitState.fromSplit(raw);
            var delivered = new java.util.concurrent.atomic.AtomicInteger();
            assertThrows(IllegalStateException.class, () -> emitter.emitRecord(
                    MySqlFetchedRecord.prepared(prepared),
                    row -> {
                        if (delivered.incrementAndGet() == 2) {
                            throw new IllegalStateException("downstream failure");
                        }
                    }, state));
            assertEquals(raw, state.checkpoint(null));
            assertNull(((MySqlSnapshotSplit) state.toMySqlSplit()).finished());
        }
    }

    @Test
    void binlogRowsShareIdentityAcrossModesAndAdvanceOnlyAfterWholeEvent() throws Exception {
        DebeziumOffset previous = position(42, 1);
        DebeziumOffset offset = position(44, 3);
        DebeziumOffset high = position(50, 5);
        List<TableRecord> rows = List.of(row(RowKind.UPDATE_BEFORE, "same"), row(RowKind.UPDATE_AFTER, "same"));

        var cdcState = MySqlSplitState.fromSplit(new MySqlBinlogSplit(previous, new byte[] {1}));
        List<TableRecord> cdcOutput = new ArrayList<>();
        binlogEmitter()
                .emitRecord(MySqlFetchedRecord.binlog(new BinlogEvent(rows, offset)), cdcOutput::add, cdcState);

        var finished = new FinishedSnapshotSplitInfo(TABLE, "chunk-0", null, null, high);
        var streaming = MySqlBinlogSplit.forCompletedSnapshot(previous, new byte[] {1}, high, List.of(finished));
        var hybridState = MySqlSplitState.fromSplit(streaming);
        var schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(100).copy(false))),
                List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9002)
                .topicPrefix("emission-test")
                .table(TABLE, schema)
                .build()
                .config();
        try (var splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(new MySqlSourceReaderContext(context()), splitReader, new TableMetrics(), config.topicPrefix());
            List<TableRecord> hybridOutput = new ArrayList<>();
            var event = MySqlFetchedRecord.binlog(new BinlogEvent(rows, offset));
            emitter.emitRecord(event, hybridOutput::add, hybridState);

            assertEquals(
                    cdcOutput.stream().map(TableRecord::changeEventId).toList(),
                    hybridOutput.stream().map(TableRecord::changeEventId).toList());
            assertEquals(offset, ((MySqlBinlogSplit) cdcState.checkpoint(new byte[] {1})).offset());
            assertEquals(offset, ((MySqlBinlogSplit) hybridState.checkpoint(new byte[] {1})).offset());

            var failedState = MySqlSplitState.fromSplit(streaming);
            assertThrows(IllegalStateException.class, () -> emitter.emitRecord(event, record -> {
                if (record.rowKind() == RowKind.UPDATE_AFTER) {
                    throw new IllegalStateException("downstream failed");
                }
            }, failedState));
            assertEquals(
                    previous,
                    ((MySqlBinlogSplit) failedState.checkpoint(new byte[] {1})).offset());
        }
    }

    @Test
    void keyedLegacyOffsetsDeliverWithoutInventingIdentity() throws Exception {
        var state = MySqlSplitState.fromSplit(new MySqlBinlogSplit(null, new byte[0]));
        var output = new ArrayList<TableRecord>();
        BinlogEvent update = new BinlogEvent(List.of(row(RowKind.INSERT, "legacy")),
                new DebeziumOffset(Map.of("server", "yak"), Map.of("file", "mysql-bin.1", "pos", 8)));
        binlogEmitter().emitRecord(MySqlFetchedRecord.binlog(update), output::add, state);
        assertNull(output.getFirst().changeEventId());
        assertEquals(update.offset(), ((MySqlBinlogSplit) state.checkpoint(new byte[] {1})).offset());
    }

    private static MySqlRecordEmitter binlogEmitter() {
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = MySqlSource.builder()
                .hostname("localhost")
                .username("user")
                .password("secret")
                .serverId(9020)
                .topicPrefix("emission-test")
                .table(TABLE, schema)
                .build()
                .config();
        var readerContext = context();
        return new MySqlRecordEmitter(new MySqlSourceReaderContext(readerContext), new MySqlSplitReader(config), new TableMetrics(), config.topicPrefix());
    }

    private static TableRecord row(RowKind kind, String value) {
        return new TableRecord(TABLE, kind, GenericRowData.of(value));
    }

    private static DebeziumOffset position(long offset, int row) {
        return new DebeziumOffset(Map.of("server", "yak"),
                Map.of("file", "mysql-bin.000004", "pos", offset, "event", 1, "row", row));
    }

    private static SourceReaderContext context() {
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
            public void sendSplitRequest() {}
        };
    }
}
