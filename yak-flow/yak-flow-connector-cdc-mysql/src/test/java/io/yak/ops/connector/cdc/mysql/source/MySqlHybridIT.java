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

    private static final TableSchema KEYLESS_SCHEMA = new TableSchema(List.of(
            new Column("bucket", LogicalTypes.BIGINT.copy(false)),
            new Column("amount", LogicalTypes.decimal(14, 2))), List.of());

    @Test
    void keylessSnapshotAndBinlogPreserveDuplicateRowsAndCrashReplay() throws Exception {
        try (Fixture fixture = new Fixture(1, 2, true)) {
            fixture.execute("INSERT INTO " + fixture.source(0)
                    + " VALUES(1,10.00),(1,10.00),(2,NULL)");
            Saved checkpoint;
            try (Harness first = new Harness(fixture, null)) {
                first.start();
                first.until(first::streaming, true);
                assertEquals(Map.of("1:10.00", 2L, "2:NULL", 1L), fixture.keylessRows(0));
                checkpoint = first.checkpoint();
                fixture.execute("DELETE FROM " + fixture.source(0)
                        + " WHERE bucket=1 AND amount=10.00 LIMIT 1",
                        "UPDATE " + fixture.source(0)
                        + " SET amount=12.00 WHERE bucket=2",
                        "INSERT INTO " + fixture.source(0) + " VALUES(3,20.00)");
                // The JDBC Sink commits these changes, but the source checkpoint is deliberately stale.
                first.until(() -> fixture.keylessRows(0).equals(
                        Map.of("1:10.00", 1L, "2:12.00", 1L, "3:20.00", 1L)), false);
            }
            // Replay from the previous checkpoint must never duplicate changes already committed
            // with durable transaction receipts.
            try (Harness recovered = new Harness(fixture, checkpoint)) {
                recovered.start();
                recovered.until(() -> recovered.streaming()
                        && fixture.keylessRows(0).equals(
                                Map.of("1:10.00", 1L, "2:12.00", 1L, "3:20.00", 1L)), true);
                fixture.execute("DELETE FROM " + fixture.source(0)
                        + " WHERE bucket=1 AND amount=10.00 LIMIT 1");
                recovered.until(() -> fixture.keylessRows(0).equals(
                        Map.of("2:12.00", 1L, "3:20.00", 1L)), true);
            }
        }
    }

    @Test
    void emptyTableFinishesSchemaBootstrapBeforeItsFirstInsert() throws Exception {
        try (Fixture fixture = new Fixture(1, 1000);
                Harness pipeline = new Harness(fixture, null)) {
            pipeline.start();
            // No source row exists when the no_data schema snapshot and Hybrid handoff run.
            pipeline.until(pipeline::streaming, true);
            assertTrue(fixture.values(0).isEmpty());
            fixture.execute("INSERT INTO " + fixture.source(0) + " VALUES(1,'first'),(2,'second'),(3,'third')");
            pipeline.until(() -> fixture.values(0).equals(Map.of(
                    1L, "first", 2L, "second", 3L, "third")), true);
            assertEquals(3, pipeline.observed.stream()
                    .filter(row -> row.rowKind() == RowKind.INSERT).count());
        }
    }

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
    void identicalPayloadRowsRemainDistinctThroughKeyMovesAndStreamingRecovery() throws Exception {
        try (Fixture fixture = new Fixture(1, 1)) {
            fixture.execute("INSERT INTO " + fixture.source(0)
                    + " VALUES(1,'same'),(2,'same'),(3,'other')");
            Saved saved;
            try (Harness first = new Harness(fixture, null)) {
                first.start();
                first.until(first::streaming, true);
                assertEquals(Map.of(1L, "same", 2L, "same", 3L, "other"), fixture.values(0));

                fixture.execute(
                        "DELETE FROM " + fixture.source(0) + " WHERE id=1",
                        "UPDATE " + fixture.source(0) + " SET id=30 WHERE id=3",
                        "INSERT INTO " + fixture.source(0) + " VALUES(4,'same')");
                first.until(() -> fixture.values(0).equals(
                        Map.of(2L, "same", 4L, "same", 30L, "other")), true);
                assertTrue(first.observed.stream().anyMatch(row -> row.rowKind() == RowKind.DELETE));
                saved = first.checkpoint();
            }
            fixture.execute(
                    "DELETE FROM " + fixture.source(0) + " WHERE id=4",
                    "UPDATE " + fixture.source(0) + " SET name='retained' WHERE id=2");
            try (Harness restored = new Harness(fixture, saved)) {
                restored.start();
                restored.until(() -> fixture.values(0).equals(
                        Map.of(2L, "retained", 30L, "other")), true);
                assertEquals(2, fixture.values(0).size());
                assertFalse(fixture.values(0).containsKey(4L));
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
            this(tableCount, chunkSize, false);
        }
        private Fixture(int tableCount, int chunkSize, boolean keyless) throws Exception {
            this.tableCount=tableCount;
            var db=DATABASE.metadata();
            Configuration options = new Configuration().set(JdbcConnectionOptions.URL, db.url())
                    .set(JdbcConnectionOptions.USERNAME, db.username()).set(JdbcConnectionOptions.PASSWORD, db.password())
                    .set(MySqlCdcSourceOptions.SERVER_ID, 7001).set(MySqlCdcSourceOptions.TOPIC_PREFIX, prefix)
                    .set(MySqlCdcSourceOptions.SNAPSHOT_CHUNK_SIZE, chunkSize);
            if (keyless) {
                options.set(MySqlCdcSourceOptions.KEYLESS_ENABLED, true);
                options.set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "bucket");
                execute("CREATE TABLE IF NOT EXISTS yak_flow_cdc_receipts "
                        + "(receipt_key BINARY(32) NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            }
            List<TableSourceFactoryContext> contexts = new ArrayList<>();
            for (int i=0;i<tableCount;i++) {
                if (keyless) {
                    execute("CREATE TABLE " + source(i)
                            + "(bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB",
                            "CREATE TABLE " + target(i)
                            + "(bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB");
                } else {
                    execute("CREATE TABLE " + source(i) + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                            "CREATE TABLE " + target(i) + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
                }
                TableId from = new TableId("yak_cdc_it", null, source(i));
                TableId to = new TableId("yak_cdc_it", null, target(i));
                TableSchema schema = keyless ? KEYLESS_SCHEMA : SCHEMA;
                contexts.add(new TableSourceFactoryContext(options.toReadonlyConfig(), from, schema, metrics));
                plans.add(new JdbcTableWritePlan(from, to, schema,
                        keyless ? JdbcWriteMode.RETRACT : JdbcWriteMode.UPSERT));
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
        private Map<String,Long> keylessRows(int table) throws Exception {
            var db=DATABASE.metadata();
            try(var connection=DriverManager.getConnection(db.url(),db.username(),db.password());
                    var sql=connection.createStatement();
                    var rows=sql.executeQuery("SELECT bucket,amount,COUNT(*) FROM "
                            + target(table) + " GROUP BY bucket,amount")) {
                Map<String,Long> result=new LinkedHashMap<>();
                while(rows.next()) {
                    java.math.BigDecimal amount=rows.getBigDecimal(2);
                    result.put(rows.getLong(1)+":"+(amount==null?"NULL":amount.toPlainString()), rows.getLong(3));
                }
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
        private MySqlHybridEnumeratorState state() throws Exception { return enumerator.snapshotState(checkpoint+1); }
        private boolean streaming() throws Exception { return state().phase()==MySqlHybridEnumeratorState.Phase.STREAMING; }
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
