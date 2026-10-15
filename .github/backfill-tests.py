from pathlib import Path
import re
C = Path('yak-flow/yak-flow-connector-cdc-mysql/src/main/java/io/yak/ops/connector/cdc/mysql/source')
T = Path('yak-flow/yak-flow-connector-cdc-mysql/src/test/java/io/yak/ops/connector/cdc/mysql/source')
def put(p, s):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(s.strip() + '\n')
def edit(p, a, b):
    s=p.read_text()
    if a not in s: raise AssertionError(f'Missing anchor in {p}: {a[:80]}')
    p.write_text(s.replace(a,b))

# Repeated bootstrap reports must not reset already completed phase measurements.
p=C/'enumerator/MySqlHybridSourceEnumerator.java'
edit(p, 'assigner.captureLow(low.offset());', '''boolean bootstrap = assigner.phase() == MySqlHybridEnumeratorState.Phase.BOOTSTRAP;
            assigner.captureLow(low.offset());
            if (!bootstrap) return;''')
# State diagnostics must not print row cursors or watermark payloads.
p=C/'enumerator/MySqlHybridEnumeratorState.java'
s=p.read_text(); p.write_text(s.rsplit('}',1)[0]+'''
    @Override public String toString() {
        return "MySqlHybridEnumeratorState{phase=" + phase + ", chunks=" + totalSnapshotSplits
                + ", checkpoint=redacted}";
    }
}
''')
p=C/'events/MySqlResumeBinlogEvent.java'
s=p.read_text(); p.write_text(s.rsplit('}',1)[0]+'''
    @Override public String toString() { return "MySqlResumeBinlogEvent{checkpoint=redacted}"; }
}
''')
p=C/'events/MySqlSnapshotFinishedEvent.java'
s=p.read_text(); p.write_text(s.rsplit('}',1)[0]+'''
    @Override public String toString() { return "MySqlSnapshotFinishedEvent{split=" + splitId() + ", checkpoint=redacted}"; }
}
''')

put(T/'split/MySqlSnapshotOutputTest.java', '''
package io.yak.ops.connector.cdc.mysql.source.split;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlHybridSplitState;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Exercises durable preparation, scalar state, and partial output without live database dependencies. */
class MySqlSnapshotOutputTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");

    @Test
    void preparationMustBeCoveredByCompletedCheckpointBeforeEmission() throws Exception {
        MySqlSnapshotSplit prepared = prepared();
        MySqlHybridSplitState state = new MySqlHybridSplitState(range());
        state.prepareSnapshot(prepared);
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(1));
        state.snapshotCheckpoint(10);
        state.snapshotCheckpoint(11);
        assertNull(state.completeCheckpoint(9));
        assertEquals("chunk", state.completeCheckpoint(10));
        state.emitSnapshotRows(1);
        MySqlSnapshotSplit partial = (MySqlSnapshotSplit) state.checkpoint(null);
        assertEquals(1, partial.outputPosition());
        assertEquals(2, partial.output().rows().size());
        assertEquals("original", partial.output().rows().getFirst().row().getString(1));
        MySqlHybridSplitState recovered = new MySqlHybridSplitState(roundTrip(partial));
        recovered.emitSnapshotRows(2);
        MySqlSnapshotSplit done = (MySqlSnapshotSplit) recovered.checkpoint(null);
        assertNull(done.output());
        assertEquals(offset(200), done.finished().highWatermark());
        assertEquals(done, roundTrip(done));
        assertNull(((MySqlSnapshotSplit) new MySqlHybridSplitState(roundTrip(done)).checkpoint(null)).output());
    }

    @Test
    void emptyChunkStillRequiresDurablePreparationAndRetainsItsHigh() {
        var prepared = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(100), List.of()));
        var state = new MySqlHybridSplitState(range());
        state.prepareSnapshot(prepared);
        assertThrows(IllegalStateException.class, () -> state.emitSnapshotRows(0));
        state.snapshotCheckpoint(3);
        state.completeCheckpoint(3);
        state.emitSnapshotRows(0);
        assertEquals(offset(100), ((MySqlSnapshotSplit) state.checkpoint(null)).finished().highWatermark());
    }

    @Test
    void normalizedRowsCannotBeMutatedThroughProducerOrReaderReferences() {
        byte[] payload = {1,2,3};
        GenericRowData mutable = GenericRowData.of(1L, "original", payload);
        var result = new MySqlSnapshotOutput(offset(100), offset(200), List.of(new TableRecord(TABLE, RowKind.INSERT, mutable)));
        mutable.setField(1, "changed");
        payload[0] = 9;
        var row = result.rows().getFirst().row();
        assertFalse(row instanceof GenericRowData);
        assertEquals("original", row.getString(1));
        byte[] exposed = (byte[]) row.getField(2);
        exposed[0] = 8;
        assertArrayEquals(new byte[]{1,2,3}, (byte[]) row.getField(2));
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertFalse(result.toString().contains("original"));
    }

    @Test
    void roundTripsAllCanonicalScalarFamiliesWithoutTypeCoercion() throws Exception {
        GenericRowData values = GenericRowData.of(1L, null, true, (byte) 2, (short) 3, 4, 5L, 1.25f, 2.5d,
                new BigDecimal("123456.78900"), "长字符串".repeat(24000), new byte[]{0,1,-1},
                LocalDate.of(2020,2,29), LocalTime.of(12,13,14,123456789),
                LocalDateTime.of(2020,2,29,12,13,14,123456789), OffsetDateTime.parse("2020-02-29T12:13:14.123456789+08:00"));
        var original = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, values))));
        assertEquals(original, roundTrip(original));
    }

    @Test
    void rejectsOldVersionTruncationUnsupportedScalarsAndTrailingBytes() throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        byte[] bytes = serializer.serialize(prepared());
        assertThrows(IOException.class, () -> serializer.deserialize(1, bytes));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, bytes.length-1)));
        assertThrows(IOException.class, () -> serializer.deserialize(serializer.getVersion(), Arrays.copyOf(bytes, bytes.length+1)));
        var unsupported = range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200),
                List.of(new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, new Object())))));
        assertThrows(IOException.class, () -> serializer.serialize(unsupported));
    }

    @Test
    void invalidOutputPositionsAndConflictingRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> prepared().withOutputPosition(3));
        var state = new MySqlHybridSplitState(range());
        var other = new MySqlSnapshotSplit("other", "fp", TABLE, null, null, null).withOutput(prepared().output());
        assertThrows(IllegalStateException.class, () -> state.prepareSnapshot(other));
        assertThrows(IllegalArgumentException.class, () -> new MySqlSnapshotOutput(offset(200), offset(100), List.of()));
    }

    private static MySqlSnapshotSplit prepared() {
        return range().withOutput(new MySqlSnapshotOutput(offset(100), offset(200), List.of(
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(1L, "original")),
                new TableRecord(TABLE, RowKind.INSERT, GenericRowData.of(2L, "second")))));
    }
    private static MySqlSnapshotSplit range() { return new MySqlSnapshotSplit("chunk", "fp", TABLE, null, null, null); }
    private static BinlogOffset offset(long pos) { return new BinlogOffset(Map.of("server","shop"), Map.of("file","mysql-bin.000001","pos",pos)); }
    private static MySqlSnapshotSplit roundTrip(MySqlSnapshotSplit split) throws Exception {
        var serializer = new MySqlHybridSplitSerializer();
        return (MySqlSnapshotSplit) serializer.deserialize(serializer.getVersion(), serializer.serialize(split));
    }
}
''')

p=T/'offset/BinlogOffsetOrderTest.java'
s=p.read_text(); p.write_text(s.rsplit('}',1)[0]+'''
    @org.junit.jupiter.api.Test
    void equalGtidSetsStillCompareBinlogFileAndPosition() {
        var partition = java.util.Map.<String,Object>of("server", "same");
        var left = new BinlogOffset(partition, java.util.Map.of("gtids", "uuid:1-5", "file", "mysql-bin.000001", "pos", 100L));
        var right = new BinlogOffset(partition, java.util.Map.of("gtids", "uuid:1-5", "file", "mysql-bin.000001", "pos", 200L));
        org.junit.jupiter.api.Assertions.assertTrue(BinlogOffsetOrder.compare(left, right) < 0);
        org.junit.jupiter.api.Assertions.assertTrue(BinlogOffsetOrder.compare(right, left) > 0);
    }
}
''')

put(T/'reader/MySqlBackfillIT.java', '''
package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real JDBC and Debezium with writes placed after scanning and before the per-chunk High. */
@Tag("cdc")
@Timeout(180)
class MySqlBackfillIT {
    @RegisterExtension static final MySqlDatabase DATABASE = MySqlDatabase.cdc();
    private static final TableSchema SCHEMA = new TableSchema(List.of(
            new Column("id", LogicalTypes.BIGINT.copy(false)),
            new Column("name", LogicalTypes.varchar(100).copy(false))), List.of("id"));

    @Test
    void backfillsRepeatedUpdatesDeletesCrossChunkMovesAndIgnoresOtherTables() throws Exception {
        String table = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        String other = table + "_other";
        execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "CREATE TABLE " + other + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "INSERT INTO " + table + " VALUES(1,'old'),(2,'deleted'),(50,'moving')");
        TableId id = new TableId("yak_cdc_it", null, table);
        TableId another = new TableId("yak_cdc_it", null, other);
        try {
            var config = config(Map.of(id, SCHEMA, another, SCHEMA), 6501, table);
            try (var reader = new MySqlSnapshotBackfillReader(config, 0, () -> {
                execute("UPDATE " + table + " SET name='intermediate' WHERE id=1",
                        "UPDATE " + table + " SET name='final' WHERE id=1",
                        "DELETE FROM " + table + " WHERE id=2",
                        "UPDATE " + table + " SET id=150 WHERE id=50",
                        "INSERT INTO " + table + " VALUES(5,'new')",
                        "INSERT INTO " + other + " VALUES(7,'not-this-chunk')");
                return null;
            })) {
                var split = reader.read(new MySqlSnapshotSplit("chunk", "fp", id, null, 100L, null));
                Map<Long,String> values = new LinkedHashMap<>();
                for (var row : split.output().rows()) {
                    assertEquals(RowKind.INSERT, row.rowKind());
                    values.put(row.row().getLong(0), row.row().getString(1));
                }
                assertEquals(Map.of(1L,"final",5L,"new"), values);
                assertTrue(BinlogOffsetOrder.compare(split.output().high(), split.output().low()) > 0);
                assertEquals(0, split.outputPosition());
            }
        } finally { execute("DROP TABLE " + table, "DROP TABLE " + other); }
    }

    @Test
    void quietEmptyChunkAndFollowingScanFinishFromActualWatermarks() throws Exception {
        String table = "quiet_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        TableId id = new TableId("yak_cdc_it", null, table);
        try (var reader = new MySqlSnapshotBackfillReader(config(Map.of(id, SCHEMA), 6601, table), 0)) {
            var empty = reader.read(new MySqlSnapshotSplit("empty", "fp", id, null, 10L, null));
            assertTrue(empty.output().rows().isEmpty());
            assertEquals(0, BinlogOffsetOrder.compare(empty.output().low(), empty.output().high()));
            execute("INSERT INTO " + table + " VALUES(20,'present')");
            var second = reader.read(new MySqlSnapshotSplit("second", "fp", id, 10L, null, null));
            assertEquals(1, second.output().rows().size());
            assertEquals(20L, second.output().rows().getFirst().row().getLong(0));
        } finally { execute("DROP TABLE " + table); }
    }

    private static MySqlCdcSourceConfig config(Map<TableId, TableSchema> schemas, int serverId, String topic) {
        var db = DATABASE.metadata();
        return new MySqlCdcSourceConfig(db.host(), db.port(), db.username(), db.password(), serverId, topic, schemas, 256);
    }
    private static void execute(String... statements) throws Exception {
        var db = DATABASE.metadata();
        try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password());
                var sql = connection.createStatement()) {
            for (String statement : statements) sql.execute(statement);
        }
    }
}
''')

put(T/'MySqlHybridIT.java', '''
package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorState;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Factory-created Hybrid Source with real Debezium, real JDBC Sink, serialized checkpoints and deterministic failure boundaries. */
@Tag("cdc")
@Timeout(240)
class MySqlHybridIT {
    @RegisterExtension static final MySqlDatabase DATABASE = MySqlDatabase.cdc();
    private static final TableSchema SCHEMA = new TableSchema(List.of(
            new Column("id", LogicalTypes.BIGINT.copy(false)),
            new Column("name", LogicalTypes.varchar(100).copy(false))), List.of("id"));

    @Test
    void factoryPipelineSnapshotsMultipleTablesThenStreamsUpdatesDeletesAndKeyMoves() throws Exception {
        try (Fixture fixture = new Fixture(2, 1)) {
            fixture.execute("INSERT INTO " + fixture.source(0) + " VALUES(1,'first'),(50,'moving')",
                    "INSERT INTO " + fixture.source(1) + " VALUES(10,'item')");
            try (Harness pipeline = new Harness(fixture, null)) {
                pipeline.start();
                pipeline.until(() -> pipeline.streaming(), true);
                assertEquals(Map.of(1L,"first",50L,"moving"), fixture.values(0));
                assertEquals(Map.of(10L,"item"), fixture.values(1));
                assertTrue(pipeline.observed.stream().allMatch(row -> row.rowKind() == RowKind.INSERT));
                assertEquals(3, pipeline.state().finishedSplitInfos().size());
                fixture.execute("UPDATE " + fixture.source(0) + " SET name='updated' WHERE id=1",
                        "UPDATE " + fixture.source(0) + " SET id=150 WHERE id=50",
                        "DELETE FROM " + fixture.source(1) + " WHERE id=10",
                        "INSERT INTO " + fixture.source(1) + " VALUES(20,'added')");
                pipeline.until(() -> fixture.values(0).equals(Map.of(1L,"updated",150L,"moving"))
                        && fixture.values(1).equals(Map.of(20L,"added")), true);
                assertTrue(pipeline.observed.stream().anyMatch(row -> row.rowKind() == RowKind.DELETE));
            }
        }
    }

    @Test
    void partialExternalWritesRecoverFrozenChunkAndApplyLaterSourceDeletion() throws Exception {
        try (Fixture fixture = new Fixture(1, 1000)) {
            StringBuilder values = new StringBuilder();
            for (int i=1;i<=600;i++) {
                if (i>1) values.append(',');
                values.append('(').append(i).append(",'original')");
            }
            fixture.execute("INSERT INTO " + fixture.source(0) + " VALUES " + values);
            Saved prepared;
            try (Harness first = new Harness(fixture, null)) {
                first.start();
                first.until(first::hasPreparedOutput, false);
                assertTrue(fixture.values(0).isEmpty(), "Prepared rows must not be written before a completed checkpoint");
                prepared = first.checkpoint();
                first.until(() -> fixture.values(0).size() >= 256, false);
                assertTrue(fixture.values(0).containsKey(1L));
                assertTrue(fixture.values(0).size() < 600, "Failure must occur after only part of the normalized output");
            }
            fixture.execute("DELETE FROM " + fixture.source(0) + " WHERE id=1",
                    "UPDATE " + fixture.source(0) + " SET name='after-high' WHERE id=300",
                    "INSERT INTO " + fixture.source(0) + " VALUES(601,'new')");
            try (Harness restored = new Harness(fixture, prepared)) {
                restored.start();
                restored.until(() -> {
                    Map<Long,String> rows = fixture.values(0);
                    return restored.streaming() && rows.size()==600 && !rows.containsKey(1L)
                            && "after-high".equals(rows.get(300L)) && "new".equals(rows.get(601L));
                }, true);
                assertEquals(600, fixture.values(0).size());
                assertFalse(fixture.values(0).containsKey(1L), "Restoration must not leave a stale row omitted by a later snapshot");
            }
        }
    }

    @Test
    void streamingRestoreKeepsChunkFiltersAndResumesMailboxEmittedOffset() throws Exception {
        try (Fixture fixture = new Fixture(2, 2)) {
            fixture.execute("INSERT INTO " + fixture.source(0) + " VALUES(1,'a')",
                    "INSERT INTO " + fixture.source(1) + " VALUES(2,'b')");
            Saved saved;
            try (Harness first = new Harness(fixture, null)) {
                first.start();
                first.until(first::streaming, true);
                fixture.execute("UPDATE " + fixture.source(0) + " SET name='before-save' WHERE id=1");
                first.until(() -> "before-save".equals(fixture.values(0).get(1L)), true);
                saved = first.checkpoint();
                assertEquals(2, first.state().finishedSplitInfos().size());
            }
            fixture.execute("DELETE FROM " + fixture.source(0) + " WHERE id=1",
                    "UPDATE " + fixture.source(1) + " SET name='after-save' WHERE id=2");
            try (Harness restored = new Harness(fixture, saved)) {
                restored.start();
                restored.until(() -> fixture.values(0).isEmpty()
                        && fixture.values(1).equals(Map.of(2L,"after-save")), true);
            }
        }
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    @FunctionalInterface private interface Condition { boolean met() throws Exception; }
    private record Saved(long checkpoint, byte[] enumerator, List<List<byte[]>> readers) {}

    private static final class Fixture implements AutoCloseable {
        private final String prefix = "hybrid_" + UUID.randomUUID().toString().replace("-", "");
        private final int tableCount;
        private final TableMetrics metrics = new TableMetrics();
        private final MySqlHybridCdcSource source;
        private final List<JdbcTableWritePlan> plans = new ArrayList<>();
        private Fixture(int tableCount, int chunkSize) throws Exception {
            this.tableCount=tableCount;
            var db=DATABASE.metadata();
            Configuration options = new Configuration().set(JdbcConnectionOptions.URL, db.url())
                    .set(JdbcConnectionOptions.USERNAME, db.username()).set(JdbcConnectionOptions.PASSWORD, db.password())
                    .set(MySqlCdcSourceOptions.SERVER_ID, 7001).set(MySqlCdcSourceOptions.TOPIC_PREFIX, prefix)
                    .set(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE, chunkSize);
            List<TableSourceFactoryContext> contexts = new ArrayList<>();
            for (int i=0;i<tableCount;i++) {
                execute("CREATE TABLE " + source(i) + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                        "CREATE TABLE " + target(i) + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
                TableId from = new TableId("yak_cdc_it", null, source(i));
                TableId to = new TableId("yak_cdc_it", null, target(i));
                contexts.add(new TableSourceFactoryContext(options.toReadonlyConfig(), from, SCHEMA, metrics));
                plans.add(new JdbcTableWritePlan(from, to, SCHEMA, JdbcWriteMode.UPSERT));
            }
            source = (MySqlHybridCdcSource) new MySqlCdcSourceFactory().createSource(contexts);
        }
        private String source(int i) { return prefix + "_s" + i; }
        private String target(int i) { return prefix + "_t" + i; }
        private void execute(String... statements) throws Exception {
            var db=DATABASE.metadata();
            try(var connection=DriverManager.getConnection(db.url(),db.username(),db.password()); var sql=connection.createStatement()) {
                for(String statement: statements) sql.execute(statement);
            }
        }
        private Map<Long,String> values(int table) throws Exception {
            var db=DATABASE.metadata();
            try(var connection=DriverManager.getConnection(db.url(),db.username(),db.password()); var sql=connection.createStatement();
                    var rows=sql.executeQuery("SELECT id,name FROM " + target(table))) {
                Map<Long,String> result=new LinkedHashMap<>();
                while(rows.next()) result.put(rows.getLong(1),rows.getString(2));
                return result;
            }
        }
        @Override public void close() throws Exception {
            for(int i=0;i<tableCount;i++) execute("DROP TABLE " + source(i), "DROP TABLE " + target(i));
        }
    }

    private static final class Harness implements AutoCloseable {
        private final Fixture fixture;
        private final Deque<CheckedAction> events = new ArrayDeque<>();
        private final List<SourceReader<TableRecord,MySqlHybridSplit>> readers = new ArrayList<>();
        private final SplitEnumerator<MySqlHybridSplit,MySqlHybridEnumeratorState> enumerator;
        private final JdbcOutputFormat output;
        private final List<TableRecord> observed = new ArrayList<>();
        private final Set<String> checkpointedPreparation = new java.util.HashSet<>();
        private long checkpoint;

        private Harness(Fixture fixture, Saved saved) throws Exception {
            this.fixture=fixture;
            var source=fixture.source;
            checkpoint=saved==null?0:saved.checkpoint();
            enumerator=saved==null?source.createEnumerator(new EnumeratorContext()):source.restoreEnumerator(new EnumeratorContext(),
                    source.getEnumeratorCheckpointSerializer().deserialize(source.getEnumeratorCheckpointSerializer().getVersion(), saved.enumerator()));
            var db=DATABASE.metadata();
            output=new JdbcOutputFormat(() -> DriverManager.getConnection(db.url(),db.username(),db.password()),
                    JdbcDialects.forUrl(db.url()), fixture.plans, fixture.metrics);
            for(int i=0;i<2;i++) {
                var reader=source.createReader(new ReaderContext(i));
                readers.add(reader);
                if(saved!=null) {
                    List<MySqlHybridSplit> splits=new ArrayList<>();
                    for(byte[] bytes:saved.readers().get(i)) splits.add(source.getSplitSerializer().deserialize(source.getSplitSerializer().getVersion(),bytes));
                    reader.addSplits(splits);
                }
            }
        }
        private void start() throws Exception {
            enumerator.start();
            for(int i=0;i<readers.size();i++) { enumerator.addReader(i); readers.get(i).start(); }
        }
        private MySqlHybridEnumeratorState state() { return enumerator.snapshotState(checkpoint+1); }
        private boolean streaming() { return state().phase()==MySqlHybridEnumeratorState.Phase.STREAMING; }
        private boolean hasPreparedOutput() throws Exception {
            for(var reader:readers) for(var split:reader.snapshotState(checkpoint+1)) {
                if(split instanceof MySqlSnapshotSplit snapshot && snapshot.output()!=null) return true;
            }
            return false;
        }
        private boolean needsPreparationCheckpoint() throws Exception {
            for(var reader:readers) for(var split:reader.snapshotState(checkpoint+1)) {
                if(split instanceof MySqlSnapshotSplit snapshot && snapshot.output()!=null
                        && !checkpointedPreparation.contains(snapshot.splitId())) return true;
            }
            return false;
        }
        private Saved checkpoint() throws Exception {
            long id=++checkpoint;
            List<List<byte[]>> states=new ArrayList<>();
            for(var reader:readers) {
                List<byte[]> encoded=new ArrayList<>();
                for(var split:reader.snapshotState(id)) {
                    encoded.add(fixture.source.getSplitSerializer().serialize(split));
                    if(split instanceof MySqlSnapshotSplit snapshot && snapshot.output()!=null) checkpointedPreparation.add(snapshot.splitId());
                }
                states.add(List.copyOf(encoded));
            }
            byte[] coordinator=fixture.source.getEnumeratorCheckpointSerializer().serialize(enumerator.snapshotState(id));
            output.flush();
            Saved saved=new Saved(id,coordinator,List.copyOf(states));
            enumerator.notifyCheckpointComplete(id);
            for(var reader:readers) reader.notifyCheckpointComplete(id);
            return saved;
        }
        private void until(Condition condition, boolean checkpointAutomatically) throws Exception {
            long deadline=System.nanoTime()+Duration.ofSeconds(100).toNanos();
            while(System.nanoTime()<deadline) {
                while(!events.isEmpty()) events.removeFirst().run();
                for(var reader:readers) {
                    assertNotEquals(InputStatus.END_OF_INPUT, reader.pollNext(row -> { observed.add(row); output.add(row); }));
                }
                output.flush();
                while(!events.isEmpty()) events.removeFirst().run();
                if(condition.met()) return;
                if(checkpointAutomatically && (needsPreparationCheckpoint()
                        || state().phase()==MySqlHybridEnumeratorState.Phase.HANDOFF)) checkpoint();
                Thread.sleep(10);
            }
            throw new AssertionError("Hybrid condition timed out; phase="+state().phase()+", rows="+observed.size());
        }
        @Override public void close() throws Exception {
            Exception failure=null;
            for(var reader:readers) try{reader.close();}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            try{enumerator.close();}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            try{output.close();}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            if(failure!=null)throw failure;
        }
        private final class ReaderContext implements SourceReaderContext {
            private final int id;
            private ReaderContext(int id){this.id=id;}
            @Override public Configuration getConfiguration(){return new Configuration().set(CheckpointingOptions.CHECKPOINTING_INTERVAL,Duration.ofSeconds(2));}
            @Override public int getIndexOfSubtask(){return id;}
            @Override public int currentParallelism(){return 2;}
            @Override public void sendSplitRequest(){events.addLast(()->enumerator.handleSplitRequest(id));}
            @Override public void sendSourceEventToCoordinator(SourceEvent event){events.addLast(()->enumerator.handleSourceEvent(id,event));}
        }
        private final class EnumeratorContext implements SplitEnumeratorContext<MySqlHybridSplit> {
            @Override public int currentParallelism(){return 2;}
            @Override public Set<Integer> registeredReaders(){return Set.of(0,1);}
            @Override public void assignSplit(MySqlHybridSplit split,int reader){events.addLast(()->readers.get(reader).addSplits(List.of(split)));}
            @Override public void signalNoMoreSplits(int reader){throw new AssertionError("Unbounded Hybrid reader cannot finish");}
            @Override public void sendEventToSourceReader(int reader,SourceEvent event){events.addLast(()->readers.get(reader).handleSourceEvents(event));}
            @Override public <V> void callAsync(Callable<V> action,BiConsumer<V,Throwable> handler){
                V result;
                try{result=action.call();}catch(Exception error){handler.accept(null,error);return;}
                handler.accept(result,null);
            }
            @Override public void runInCoordinatorThread(Runnable action){action.run();}
        }
    }
}
''')
# Require positive JUnit evidence for every newly owned database test.
p=Path('scripts/tests/backend_tests.py')
edit(p, '"MySqlBinlogIT": 1, "MySqlHybridIT": 1', '"MySqlBinlogIT": 1, "MySqlHybridIT": 3, "MySqlBackfillIT": 2')
