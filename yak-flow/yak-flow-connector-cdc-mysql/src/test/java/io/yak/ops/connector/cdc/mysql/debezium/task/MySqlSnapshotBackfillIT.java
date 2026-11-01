package io.yak.ops.connector.cdc.mysql.debezium.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaAssembledEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitMetaRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateAckEvent;
import io.yak.ops.connector.cdc.mysql.source.events.BinlogSplitUpdateRequestEvent;
import io.yak.ops.connector.cdc.mysql.source.events.MySqlResumeBinlogEvent;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlRecordEmitter;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReader;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSourceReaderContext;
import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSplitReader;
import io.yak.ops.connector.cdc.mysql.source.split.FinishedSnapshotSplitInfo;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlFetchedRecord;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSplitState;
import io.yak.ops.core.api.connector.source.SourceEvent;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.data.TableRecord;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import io.yak.ops.connector.cdc.mysql.source.MySqlSource;
import io.yak.ops.connector.cdc.mysql.source.assigners.ChunkRange;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypeRoot;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Exercises the real MySQL read view and Debezium LOW/HIGH bounded backfill.
 *
 * <p>The phase callbacks commit on a separate JDBC connection at deterministic
 * boundaries, instead of racing the snapshot thread with arbitrary sleeps.
 * This is a Source contract test; a completed checkpoint and transactional Sink
 * are covered independently by the runtime and backend acceptance suites.
 */
@Tag("cdc")
@Timeout(180)
class MySqlSnapshotBackfillIT {
    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-snapshot-backfill", true, false);

    private static final TableId TABLE = new TableId("yak_cdc_it", null, "snapshot_contract");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(
                    new Column("id", LogicalTypes.BIGINT.copy(false)),
                    new Column("name", LogicalTypes.varchar(64).copy(false))),
            List.of("id"));

    @BeforeEach
    void createFreshTable() throws SQLException {
        execute("DROP TABLE IF EXISTS snapshot_contract");
        execute("CREATE TABLE snapshot_contract "
                + "(id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL) ENGINE=InnoDB");
    }

    @Test
    void reconcilesCommitsBetweenLowAndReadViewAndBetweenSnapshotAndHigh() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'before'),(10,'deleted')");

        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(
                config(),
                0,
                () -> {
                    execute("UPDATE snapshot_contract SET name='updated' WHERE id=1");
                    execute("DELETE FROM snapshot_contract WHERE id=10");
                    execute("INSERT INTO snapshot_contract VALUES (3,'after')");
                    return null;
                },
                () -> {
                    // Visible in the consistent scan and also in (LOW, HIGH].
                    execute("INSERT INTO snapshot_contract VALUES (2,'low-gap')");
                    return null;
                },
                () -> null)) {
            MySqlSnapshotSplit prepared = reader.read(split(null, null));
            assertThat(prepared.output()).isNotNull();
            assertThat(prepared.output().high().compareTo(prepared.output().low())).isGreaterThan(0);
            assertThat(values(prepared)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(1L, "updated", 2L, "low-gap", 3L, "after"));
        }
    }

    @Test
    void snapshotPredicateDoesNotFilterSubsequentOriginalBinlogMutations() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'included'),(2,'outside-snapshot')");
        MySqlSourceConfig selected = config(SCHEMA, new MySqlTableOptions(false, "id = 1", true, true, true));
        MySqlSnapshotSplit prepared;
        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(selected, 0)) {
            prepared = reader.read(split(null, null));
            assertThat(values(prepared)).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "included"));
        }
        assertMutationAfterSnapshot(selected, prepared,
                "UPDATE snapshot_contract SET name='incremental-visible' WHERE id=2", 2L, "incremental-visible");
    }

    @Test
    void skippedSnapshotPersistsAnEmptyWatermarkFenceAndStartsIncrementalFromIt() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'not-loaded'),(2,'not-loaded')");
        MySqlSourceConfig selected = config(SCHEMA, new MySqlTableOptions(true, null, true, true, true));
        MySqlSnapshotSplit prepared;
        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(selected, 0)) {
            prepared = reader.read(split(null, null));
            assertThat(prepared.output().rows()).isEmpty();
            assertThat(prepared.output().low()).isEqualTo(prepared.output().high());
            assertThat(prepared.output().schemaHistory()).isNotEmpty();
        }
        assertMutationAfterSnapshot(selected, prepared,
                "INSERT INTO snapshot_contract VALUES (3,'after-skip')", 3L, "after-skip");
    }

    private static void assertMutationAfterSnapshot(MySqlSourceConfig config, MySqlSnapshotSplit prepared,
            String mutation, long id, String value) throws Exception {
        MySqlSnapshotSplit finished = prepared.withOutputPosition(prepared.output().rows().size());
        var high = finished.finished().highWatermark();
        var initial = io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit.forCompletedSnapshot(
                high, finished.finishedSchemaHistory(), high, List.of(finished.finished()));
        execute(mutation);
        try (MySqlBinlogSplitReadTask binlog = new MySqlBinlogSplitReadTask(config)) {
            binlog.start(initial);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
            boolean received = false;
            while (System.nanoTime() < deadline && !received) {
                var event = binlog.poll();
                received = event != null && event.records().stream().anyMatch(row ->
                        ((Number) row.row().getField(0)).longValue() == id
                                && value.equals(row.row().getString(1)));
            }
            assertThat(received).as("Mutation after the persisted snapshot boundary").isTrue();
        }
    }

    @Test
    void reconcilesPrimaryKeyMovesAcrossHalfOpenChunkBounds() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (8,'outside'),(12,'inside')");

        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(
                config(), 0, () -> {
                    execute("UPDATE snapshot_contract SET id=22 WHERE id=12");
                    execute("UPDATE snapshot_contract SET id=13 WHERE id=8");
                    return null;
                })) {
            MySqlSnapshotSplit prepared = reader.read(split(10L, 20L));
            assertThat(values(prepared)).containsExactlyInAnyOrderEntriesOf(Map.of(13L, "outside"));
        }
    }

    @Test
    void failureAfterScanCannotPublishPartiallyReconciledRows() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'before')");

        MySqlSnapshotSplit assigned = split(null, null);
        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(
                config(), 0, () -> {
                    throw new IllegalStateException("injected failure after JDBC scan");
                })) {
            assertThatThrownBy(() -> reader.read(assigned))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("injected failure");
        }
        // A failed attempt does not mutate the assigned split or publish a finished fence.
        assertThat(assigned.prepared()).isFalse();
        assertThat(assigned.finished()).isNull();
        assertThat(assigned.output()).isNull();

        // The full range can be scanned again after the failed attempt is closed.
        try (MySqlSnapshotSplitReadTask restored = new MySqlSnapshotSplitReadTask(config(), 0)) {
            assertThat(values(restored.read(assigned))).containsExactlyInAnyOrderEntriesOf(
                    Map.of(1L, "before"));
        }
    }

    @Test
    void deferredInitialBinlogRecoversFromSnapshotHighThroughDdlAndReaderRestart() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'snapshot')");
        MySqlSnapshotSplit finished;
        try (MySqlSnapshotSplitReadTask snapshotReader = new MySqlSnapshotSplitReadTask(config(), 0)) {
            MySqlSnapshotSplit prepared = snapshotReader.read(split(null, null));
            assertThat(prepared.output().schemaHistory()).isNotEmpty();
            finished = prepared.withOutputPosition(prepared.output().rows().size());
        }
        assertThat(finished.finished()).isNotNull();
        assertThat(finished.finishedSchemaHistory()).isNotEmpty();
        var high = finished.finished().highWatermark();
        var initial = io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit.forCompletedSnapshot(
                high, finished.finishedSchemaHistory(), high, List.of(finished.finished()));
        // Changes after the Chunk HIGH must be decoded by the deferred Binlog reader.
        execute("ALTER TABLE snapshot_contract ALTER COLUMN name SET DEFAULT 'after-ddl'");
        execute("INSERT INTO snapshot_contract (id) VALUES (2)");

        io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit checkpoint;
        try (MySqlBinlogSplitReadTask binlog = new MySqlBinlogSplitReadTask(config())) {
            binlog.start(initial);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
            io.yak.ops.connector.cdc.mysql.debezium.BinlogEvent delivered = null;
            while (System.nanoTime() < deadline && delivered == null) {
                var event = binlog.poll();
                if (event != null && event.records().stream().anyMatch(
                        record -> ((Number) record.row().getField(0)).longValue() == 2L)) {
                    delivered = event;
                }
            }
            assertThat(delivered).as("Binlog event after Snapshot HIGH and DDL").isNotNull();
            assertThat(delivered.records()).anySatisfy(row -> {
                if (((Number) row.row().getField(0)).longValue() == 2L) {
                    assertThat(row.row().getString(1)).isEqualTo("after-ddl");
                }
            });
            byte[] currentHistory = binlog.snapshotHistory();
            checkpoint = initial.withEmittedOffset(delivered.offset(), currentHistory);
            var serializer = MySqlSplitSerializer.INSTANCE;
            checkpoint = (io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit)
                    serializer.deserialize(serializer.getVersion(), serializer.serialize(checkpoint));
        }
        execute("INSERT INTO snapshot_contract VALUES (3,'replayed')");
        try (MySqlBinlogSplitReadTask restored = new MySqlBinlogSplitReadTask(config())) {
            restored.start(checkpoint);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
            boolean received = false;
            while (System.nanoTime() < deadline && !received) {
                var event = restored.poll();
                if (event != null) {
                    received = event.records().stream().anyMatch(row ->
                            ((Number) row.row().getField(0)).longValue() == 3L
                                    && "replayed".equals(row.row().getString(1)));
                }
            }
            assertThat(received).as("Checkpointed Binlog resumes after DDL").isTrue();
        }
    }

    @Test
    void restoredStreamingSplitPreservesPendingPauseAndResumesAfterMetadataAcknowledgement() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'snapshot')");
        MySqlSourceConfig sourceConfig = config();
        MySqlSnapshotSplit finished;
        try (MySqlSnapshotSplitReadTask snapshotReader = new MySqlSnapshotSplitReadTask(sourceConfig, 0)) {
            MySqlSnapshotSplit prepared = snapshotReader.read(split(null, null));
            finished = prepared.withOutputPosition(prepared.output().rows().size());
        }
        var high = finished.finished().highWatermark();
        MySqlBinlogSplit checkpoint = MySqlBinlogSplit.forCompletedSnapshot(
                high, finished.finishedSchemaHistory(), high, List.of(finished.finished()));
        MySqlSplitSerializer serializer = MySqlSplitSerializer.INSTANCE;
        checkpoint = (MySqlBinlogSplit) serializer.deserialize(
                serializer.getVersion(), serializer.serialize(checkpoint));
        List<SourceEvent> events = new ArrayList<>();
        List<TableRecord> rows = new ArrayList<>();
        long updateGeneration = 17;

        try (MySqlSourceReader restored = new MySqlSourceReader(
                sourceConfig, context(events), new TableMetrics(), true)) {
            restored.start();
            // The mailbox can receive the coordinator's restored-generation update
            // before the asynchronous fetcher installs the checkpointed Binlog split.
            restored.handleSourceEvents(new BinlogSplitUpdateRequestEvent(updateGeneration));
            restored.addSplits(List.of(checkpoint));
            pollUntil(restored, rows, () -> events.stream().anyMatch(event ->
                    event instanceof BinlogSplitMetaRequestEvent request
                            && request.getGeneration() == updateGeneration));

            // This request is sent only after the mailbox consumes the fetcher's
            // finished marker, removes the active split and stores its paused state.
            assertThat(rows).isEmpty();
            assertThat(restored.snapshotState(1)).singleElement().satisfies(split -> {
                assertThat(split).isInstanceOf(MySqlBinlogSplit.class);
                MySqlBinlogSplit paused = (MySqlBinlogSplit) split;
                assertThat(paused.phase()).isEqualTo(MySqlBinlogSplit.Phase.PAUSED);
                assertThat(paused.offset()).isEqualTo(high);
                assertThat(paused.finishedSplits()).hasSize(1);
                assertThat(paused.schemaHistory()).isNotEmpty();
            });
            restored.handleSourceEvents(new BinlogSplitMetaEvent(
                    0, 1, 1, updateGeneration,
                    List.of(FinishedSnapshotSplitInfo.serialize(finished.finished()))));
            assertThat(events).anyMatch(event -> event instanceof BinlogSplitMetaAssembledEvent assembled
                    && assembled.getBinlogAssignmentGeneration() == updateGeneration);
            restored.handleSourceEvents(new MySqlResumeBinlogEvent(high));
            assertThat(events).anyMatch(event -> event instanceof BinlogSplitUpdateAckEvent acknowledgement
                    && acknowledgement.getGeneration() == updateGeneration);

            execute("INSERT INTO snapshot_contract VALUES (2,'after-explicit-resume')");
            pollUntil(restored, rows, () -> rows.stream().anyMatch(row ->
                    ((Number) row.row().getField(0)).longValue() == 2L
                            && "after-explicit-resume".equals(row.row().getString(1))));
            assertThat(rows).noneMatch(row -> ((Number) row.row().getField(0)).longValue() == 1L);
            assertThat(restored.snapshotState(2)).singleElement().satisfies(split ->
                    assertThat(((MySqlBinlogSplit) split).phase()).isEqualTo(MySqlBinlogSplit.Phase.STREAMING));
        }
    }

    private static void pollUntil(MySqlSourceReader reader, List<TableRecord> rows, BooleanSupplier completed)
            throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
        while (!completed.getAsBoolean()) {
            long remaining = deadline - System.nanoTime();
            assertThat(remaining).as("Reader must complete the requested metadata or data transition").isPositive();
            reader.isAvailable().get(remaining, TimeUnit.NANOSECONDS);
            reader.pollNext(rows::add);
        }
    }

    @Test
    void realMysqlWideChunkLargerThanFourMiBCompletesWithoutDroppingImages() throws Exception {
        execute("ALTER TABLE snapshot_contract MODIFY name VARCHAR(16000) NOT NULL");
        String payload = "w".repeat(12_000);
        try (var connection = MYSQL.metadata().connect();
                var statement = connection.prepareStatement(
                        "INSERT INTO snapshot_contract (id,name) VALUES (?,?)")) {
            for (int index = 0; index < 400; index++) {
                statement.setLong(1, index);
                statement.setString(2, payload);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        var wideSchema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(16_000).copy(false))),
                List.of("id"));
        try (MySqlSnapshotSplitReadTask reader = new MySqlSnapshotSplitReadTask(config(wideSchema), 0)) {
            MySqlSnapshotSplit prepared = reader.read(split(null, null));
            assertThat(prepared.output().rows()).hasSize(400);
            assertThat(prepared.output().rows())
                    .allSatisfy(row -> assertThat(row.row().getString(1)).isEqualTo(payload));
        }
    }

    @Test
    void failedMidChunkCollectionDoesNotCheckpointFinishedFenceAndCanReplay() throws Exception {
        execute("INSERT INTO snapshot_contract VALUES (1,'first'),(2,'second')");
        var config = config();
        MySqlSnapshotSplit assigned = split(null, null);
        MySqlSnapshotSplit prepared;
        try (MySqlSnapshotSplitReadTask task = new MySqlSnapshotSplitReadTask(config, 0)) {
            prepared = task.read(assigned);
        }

        try (MySqlSplitReader splitReader = new MySqlSplitReader(config)) {
            var emitter = new MySqlRecordEmitter(
                    new MySqlSourceReaderContext(context()),
                    splitReader,
                    new TableMetrics(),
                    config.topicPrefix());
            MySqlSplitState incomplete = MySqlSplitState.fromSplit(assigned);
            List<TableRecord> delivered = new ArrayList<>();
            AtomicInteger attempts = new AtomicInteger();

            assertThatThrownBy(() -> emitter.emitRecord(
                    MySqlFetchedRecord.prepared(prepared),
                    row -> {
                        if (attempts.incrementAndGet() == 2) {
                            throw new IllegalStateException("injected collector failure");
                        }
                        delivered.add(row);
                    },
                    incomplete))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("injected collector failure");

            var serializer = MySqlSplitSerializer.INSTANCE;
            MySqlSnapshotSplit persisted = (MySqlSnapshotSplit) serializer.deserialize(
                    serializer.getVersion(), serializer.serialize(incomplete.checkpoint(null)));
            assertThat(persisted.finished()).isNull();
            assertThat(persisted.output()).isNull();
            assertThat(persisted.chunkRange()).isEqualTo(assigned.chunkRange());

            // A failed collector did not persist any normalized row images. A restored
            // reader must re-scan the original range, not resume at emitted ordinal 1.
            MySqlSnapshotSplit retried;
            try (MySqlSnapshotSplitReadTask restored = new MySqlSnapshotSplitReadTask(config, 0)) {
                retried = restored.read(persisted);
            }
            MySqlSplitState recovered = MySqlSplitState.fromSplit(persisted);
            List<TableRecord> replayed = new ArrayList<>();
            emitter.emitRecord(MySqlFetchedRecord.prepared(retried), replayed::add, recovered);
            assertThat(replayed).hasSize(2);
            assertThat(replayed.getFirst().changeEventId()).isEqualTo(
                    delivered.getFirst().changeEventId());
            MySqlSnapshotSplit finished = (MySqlSnapshotSplit) serializer.deserialize(
                    serializer.getVersion(), serializer.serialize(recovered.checkpoint(null)));
            assertThat(finished.finished()).isNotNull();
            assertThat(finished.output()).isNull();
        }
    }

    private static SourceReaderContext context() {
        return context(new ArrayList<>());
    }

    private static SourceReaderContext context(List<SourceEvent> events) {
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

            @Override
            public void sendSourceEventToCoordinator(SourceEvent event) {
                events.add(event);
            }
        };
    }

    private static MySqlSnapshotSplit split(Long start, Long end) {
        return MySqlSnapshotSplit.typed(
                "snapshot-contract-" + (start == null ? "first" : start),
                new ChunkRange(TABLE, "id", LogicalTypeRoot.BIGINT, start, end));
    }

    private static Map<Long, String> values(MySqlSnapshotSplit prepared) {
        return prepared.output().rows().stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.row().getField(0)).longValue(),
                        row -> row.row().getString(1)));
    }

    private static MySqlSourceConfig config() {
        return config(SCHEMA);
    }

    private static MySqlSourceConfig config(TableSchema schema) {
        return config(schema, MySqlTableOptions.DEFAULT);
    }

    private static MySqlSourceConfig config(TableSchema schema, MySqlTableOptions tableOptions) {
        var database = MYSQL.metadata();
        return MySqlSource.builder()
                .hostname(database.host())
                .port(database.port())
                .username(database.username())
                .password(database.password())
                .serverId(23_400)
                .topicPrefix("yak-snapshot-contract")
                .connectionProperties(Map.of("useSSL", "false", "allowPublicKeyRetrieval", "true"))
                .table(TABLE, schema)
                .tableOptions(TABLE, tableOptions)
                .buildHybrid(32)
                .config();
    }

    private static void execute(String sql) throws SQLException {
        MYSQL.metadata().execute(sql);
    }
}
