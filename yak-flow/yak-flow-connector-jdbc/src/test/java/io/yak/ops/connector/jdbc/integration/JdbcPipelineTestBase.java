package io.yak.ops.connector.jdbc.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.checkpoint.CheckpointSnapshot;
import io.yak.ops.flow.runtime.checkpoint.LocalCompletedCheckpointStore;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.TestNames;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Real JDBC pipelines: multi-table routing, final checkpoint and restore without Boot. */
@Tag("pipeline")
@Timeout(240)
abstract class JdbcPipelineTestBase {
    protected abstract DatabaseMetadata sourceDatabase();

    protected abstract DatabaseMetadata targetDatabase();

    private static final SinkWriter.Context RECORD_CONTEXT = new SinkWriter.Context() {
        @Override
        public Long timestamp() {
            return null;
        }

        @Override
        public long currentWatermark() {
            return Long.MIN_VALUE;
        }
    };

    @TempDir
    Path checkpointDirectory;

    @Test
    void finalCheckpointPersistsAndRestoresCompletedJdbcPipeline() throws Exception {

        JdbcConnectionOptions sourceOptions = connectionOptions("source");
        JdbcConnectionOptions targetOptions = connectionOptions("target");
        JdbcDialect sourceDialect = JdbcDialects.forUrl(sourceOptions.url());
        JdbcDialect targetDialect = JdbcDialects.forUrl(targetOptions.url());
        TableSchema sourceSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                        new Column("LABEL", LogicalTypes.varchar(48))),
                List.of("ID"));
        TableSchema targetSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                        new Column("MESSAGE", LogicalTypes.varchar(48))),
                List.of("ID"));

        try (Connection sourceConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(sourceOptions)
                                .getConnection();
                Connection targetConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(targetOptions)
                                .getConnection();
                Statement sourceDdl = sourceConnection.createStatement();
                Statement targetDdl = targetConnection.createStatement()) {
            TableId sourceTable = tableId(
                    sourceConnection,
                    sourceOptions.url(),
                    TestNames.unique("pipe").toUpperCase(java.util.Locale.ROOT));
            TableId targetTable = tableId(
                    targetConnection,
                    targetOptions.url(),
                    TestNames.unique("pipe").toUpperCase(java.util.Locale.ROOT));
            try {
                sourceDdl.execute(sourceDialect.createTableSql(sourceTable, sourceSchema));
                targetDdl.execute(targetDialect.createTableSql(targetTable, targetSchema));
                insertRows(
                        sourceConnection,
                        sourceDialect,
                        sourceSchema,
                        sourceTable,
                        null,
                        1L,
                        2L,
                        3L,
                        4L,
                        5L,
                        6L,
                        7L,
                        8L,
                        9L,
                        10L,
                        11L,
                        12L);

                AtomicBoolean restoring = new AtomicBoolean();
                AtomicInteger openedSourceConnections = new AtomicInteger();
                AtomicInteger createdWriters = new AtomicInteger();
                AtomicInteger finalFlushes = new AtomicInteger();
                JdbcConnectionProvider sourceConnections = () -> {
                    if (restoring.get()) {
                        throw new AssertionError("Finished-on-Restore must not open JDBC Source connections");
                    }
                    openedSourceConnections.incrementAndGet();
                    return new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(
                                    sourceOptions)
                            .getConnection();
                };
                Configuration configuration = new Configuration();
                configuration.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 3);
                configuration.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 8);
                configuration.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofHours(1));
                configuration.set(CheckpointingOptions.CHECKPOINTING_TIMEOUT, Duration.ofMinutes(2));
                configuration.set(CheckpointingOptions.STATE_DIRECTORY, checkpointDirectory.toString());
                JdbcSource jdbcSource =
                        new JdbcSource(sourceConnections, sourceOptions.url(), List.of(sourceTable), configuration);
                JdbcSink jdbcSink = JdbcSink.builder()
                        .withConnectionOptions(targetOptions)
                        .withTablePlans(List.of(new JdbcTableWritePlan(
                                sourceTable,
                                targetTable,
                                targetSchema,
                                JdbcWriteMode.UPSERT,
                                sourceSchema,
                                Map.of("MESSAGE", "LABEL"))))
                        .withBatchFlushPolicy(new BatchFlushPolicy(64, Duration.ZERO))
                        .build();
                Sink<TableRecord> observedSink = context -> {
                    if (restoring.get()) {
                        throw new AssertionError("Finished-on-Restore must not create a JDBC SinkWriter");
                    }
                    createdWriters.incrementAndGet();
                    JdbcWriter writer = jdbcSink.createWriter(context);
                    return new SinkWriter<>() {
                        @Override
                        public void write(TableRecord record, Context metadata) throws Exception {
                            writer.write(record, metadata);
                        }

                        @Override
                        public void flush(boolean endOfInput) throws Exception {
                            if (endOfInput) {
                                finalFlushes.incrementAndGet();
                            }
                            writer.flush(endOfInput);
                        }

                        @Override
                        public void close() throws Exception {
                            writer.close();
                        }
                    };
                };
                SourceTransformation<TableRecord> sourceNode =
                        new SourceTransformation<>("jdbc-jdbc-source", jdbcSource, TableRecord.class, 2);
                SinkTransformation<TableRecord> sinkNode =
                        new SinkTransformation<>(sourceNode, "jdbc-jdbc-sink", observedSink, 1);
                sourceNode.setUid("jdbc-jdbc-source");
                sinkNode.setUid("jdbc-jdbc-sink");
                StreamGraph graph = new StreamGraphGenerator(sinkNode, configuration).generate();

                JobClient first = new EmbeddedPipelineExecutor()
                        .execute(graph, configuration)
                        .get(30, TimeUnit.SECONDS);
                first.getJobExecutionResult().get(120, TimeUnit.SECONDS);
                assertEquals(JobStatus.FINISHED, first.getJobStatus().get(10, TimeUnit.SECONDS));
                assertEquals(1, createdWriters.get());
                assertEquals(1, finalFlushes.get());
                assertTrue(openedSourceConnections.get() > 0);
                Map<Long, List<String>> expected = new LinkedHashMap<>();
                for (long key = 1; key <= 12; key++) {
                    expected.put(key, List.of("row-" + key));
                }
                assertEquals(expected, rows(targetConnection, targetDialect, targetTable, false));

                try (LocalCompletedCheckpointStore store = new LocalCompletedCheckpointStore(checkpointDirectory)) {
                    CheckpointSnapshot published = store.loadLatest(LocalCompletedCheckpointStore.graphSignature(graph))
                            .orElseThrow();
                    assertEquals(
                            java.util.Set.of("jdbc-jdbc-source", "jdbc-jdbc-sink"), published.fullyFinishedOperators());
                    assertTrue(published.finishedSubtasks().isEmpty());
                }

                // Data added after a completed terminal checkpoint must not be replayed on restore.
                insertRows(sourceConnection, sourceDialect, sourceSchema, sourceTable, null, 99L);
                restoring.set(true);
                int priorConnections = openedSourceConnections.get();
                Configuration recovery = new Configuration(configuration);
                recovery.set(CheckpointingOptions.RESTORE_LATEST, true);
                JobClient second =
                        new EmbeddedPipelineExecutor().execute(graph, recovery).get(30, TimeUnit.SECONDS);
                second.getJobExecutionResult().get(120, TimeUnit.SECONDS);
                assertEquals(JobStatus.FINISHED, second.getJobStatus().get(10, TimeUnit.SECONDS));
                assertEquals(priorConnections, openedSourceConnections.get());
                assertEquals(1, createdWriters.get());
                assertEquals(1, finalFlushes.get());
                assertEquals(expected, rows(targetConnection, targetDialect, targetTable, false));
            } finally {
                dropIfPresent(targetDdl, targetDialect, targetTable);
                dropIfPresent(sourceDdl, sourceDialect, sourceTable);
            }
        }
    }

    @Test
    void transfersMultipleTablesAndAppliesChangelogAcrossDatabaseVendors() throws Exception {
        JdbcConnectionOptions sourceOptions = connectionOptions("source");
        JdbcConnectionOptions targetOptions = connectionOptions("target");
        JdbcDialect sourceDialect = JdbcDialects.forUrl(sourceOptions.url());
        JdbcDialect targetDialect = JdbcDialects.forUrl(targetOptions.url());

        TableSchema sourceASchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                        new Column("LABEL", LogicalTypes.varchar(48))),
                List.of("ID"));
        TableSchema sourceBSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                        new Column("LABEL", LogicalTypes.varchar(48)),
                        new Column("CATEGORY", LogicalTypes.varchar(24))),
                List.of("ID"));
        TableSchema targetASchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                        new Column("MESSAGE", LogicalTypes.varchar(48))),
                List.of("ID"));
        TableSchema targetBSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.decimal(18, 0).copy(false)),
                        new Column("MESSAGE", LogicalTypes.varchar(48)),
                        new Column("CATEGORY", LogicalTypes.varchar(24))),
                List.of("ID"));

        try (Connection sourceConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(sourceOptions)
                                .getConnection();
                Connection targetConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(targetOptions)
                                .getConnection();
                Statement sourceDdl = sourceConnection.createStatement();
                Statement targetDdl = targetConnection.createStatement()) {
            TableId sourceA = tableId(
                    sourceConnection,
                    sourceOptions.url(),
                    TestNames.unique("pipe").toUpperCase(java.util.Locale.ROOT));
            TableId sourceB = tableId(
                    sourceConnection,
                    sourceOptions.url(),
                    TestNames.unique("pipe").toUpperCase(java.util.Locale.ROOT));
            TableId targetA = tableId(
                    targetConnection,
                    targetOptions.url(),
                    TestNames.unique("pipe").toUpperCase(java.util.Locale.ROOT));
            TableId targetB = tableId(
                    targetConnection,
                    targetOptions.url(),
                    TestNames.unique("pipe").toUpperCase(java.util.Locale.ROOT));
            try {
                sourceDdl.execute(sourceDialect.createTableSql(sourceA, sourceASchema));
                sourceDdl.execute(sourceDialect.createTableSql(sourceB, sourceBSchema));
                targetDdl.execute(targetDialect.createTableSql(targetA, targetASchema));
                targetDdl.execute(targetDialect.createTableSql(targetB, targetBSchema));
                insertRows(sourceConnection, sourceDialect, sourceASchema, sourceA, null, 1L, 2L, 3L);
                insertRows(sourceConnection, sourceDialect, sourceBSchema, sourceB, "priority", 4L, 5L);

                JdbcSink sink = JdbcSink.builder()
                        .withConnectionOptions(targetOptions)
                        .withTablePlans(List.of(
                                new JdbcTableWritePlan(
                                        sourceA,
                                        targetA,
                                        targetASchema,
                                        JdbcWriteMode.UPSERT,
                                        sourceASchema,
                                        Map.of("MESSAGE", "LABEL")),
                                new JdbcTableWritePlan(
                                        sourceB,
                                        targetB,
                                        targetBSchema,
                                        JdbcWriteMode.UPSERT,
                                        sourceBSchema,
                                        Map.of("MESSAGE", "LABEL"))))
                        .withBatchFlushPolicy(new BatchFlushPolicy(3, Duration.ZERO))
                        .build();

                Configuration configuration = new Configuration();
                configuration.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 2);
                JdbcSource source = new JdbcSource(sourceOptions, List.of(sourceA, sourceB), configuration);
                SourceTransformation<TableRecord> sourceNode =
                        new SourceTransformation<>("real-cross-database-source", source, TableRecord.class, 2);
                SinkTransformation<TableRecord> sinkNode =
                        new SinkTransformation<>(sourceNode, "real-cross-database-sink", sink, 1);
                StreamGraph graph = new StreamGraphGenerator(sinkNode, configuration).generate();
                JobClient job = new EmbeddedPipelineExecutor()
                        .execute(graph, configuration)
                        .get(30, TimeUnit.SECONDS);
                job.getJobExecutionResult().get(120, TimeUnit.SECONDS);
                assertEquals(JobStatus.FINISHED, job.getJobStatus().get(10, TimeUnit.SECONDS));
                assertEquals(
                        Map.of(
                                1L, List.of("row-1"),
                                2L, List.of("row-2"),
                                3L, List.of("row-3")),
                        rows(targetConnection, targetDialect, targetA, false));
                assertEquals(
                        Map.of(
                                4L, List.of("row-4", "priority"),
                                5L, List.of("row-5", "priority")),
                        rows(targetConnection, targetDialect, targetB, true));

                // Only primary-key changes delete the old row; same-key updates use UPSERT.
                try (JdbcWriter writer = sink.createWriter(context())) {
                    writer.write(record(sourceA, RowKind.UPDATE_BEFORE, 1L, "row-1"), RECORD_CONTEXT);
                    writer.write(record(sourceA, RowKind.UPDATE_AFTER, 101L, "renamed"), RECORD_CONTEXT);
                    writer.write(record(sourceB, RowKind.DELETE, 4L, null, "priority"), RECORD_CONTEXT);
                    writer.flush(false);
                }
                Map<Long, List<String>> expectedOrders = Map.of(
                        2L, List.of("row-2"),
                        3L, List.of("row-3"),
                        101L, List.of("renamed"));
                Map<Long, List<String>> expectedItems = Map.of(5L, List.of("row-5", "priority"));
                assertEquals(expectedOrders, rows(targetConnection, targetDialect, targetA, false));
                assertEquals(expectedItems, rows(targetConnection, targetDialect, targetB, true));

                // Reapply committed records to verify writer-level UPSERT/DELETE idempotence.
                try (JdbcWriter replay = sink.createWriter(context())) {
                    replay.write(record(sourceA, RowKind.INSERT, 101L, "renamed"), RECORD_CONTEXT);
                    replay.write(record(sourceB, RowKind.DELETE, 4L, null, "priority"), RECORD_CONTEXT);
                    replay.flush(false);
                }
                assertEquals(expectedOrders, rows(targetConnection, targetDialect, targetA, false));
                assertEquals(expectedItems, rows(targetConnection, targetDialect, targetB, true));
            } finally {
                dropIfPresent(targetDdl, targetDialect, targetB);
                dropIfPresent(targetDdl, targetDialect, targetA);
                dropIfPresent(sourceDdl, sourceDialect, sourceB);
                dropIfPresent(sourceDdl, sourceDialect, sourceA);
            }
        }
    }

    private JdbcConnectionOptions connectionOptions(String role) {
        DatabaseMetadata database = role.equals("source") ? sourceDatabase() : targetDatabase();
        return new JdbcConnectionOptions(database.url(), database.username(), database.password());
    }

    private static TableId tableId(Connection connection, String url, String table) throws SQLException {
        boolean mysql = url.startsWith("jdbc:mysql:");
        return new TableId(mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), table);
    }

    private static void insertRows(
            Connection connection,
            JdbcDialect dialect,
            TableSchema schema,
            TableId table,
            String category,
            long... keys)
            throws SQLException {
        JdbcDialectConverter converter = dialect.createRowConverter(schema);
        try (PreparedStatement statement = connection.prepareStatement(dialect.insertSql(table, schema))) {
            for (long key : keys) {
                GenericRowData row = category == null
                        ? GenericRowData.of(BigDecimal.valueOf(key), "row-" + key)
                        : GenericRowData.of(BigDecimal.valueOf(key), "row-" + key, category);
                converter.toExternal(row, statement);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static TableRecord record(TableId table, RowKind kind, long key, String label) {
        return new TableRecord(table, kind, GenericRowData.of(BigDecimal.valueOf(key), label));
    }

    private static TableRecord record(TableId table, RowKind kind, long key, String label, String category) {
        return new TableRecord(table, kind, GenericRowData.of(BigDecimal.valueOf(key), label, category));
    }

    private static Map<Long, List<String>> rows(
            Connection connection, JdbcDialect dialect, TableId table, boolean withCategory) throws SQLException {
        String query = "SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("MESSAGE")
                + (withCategory ? ", " + dialect.quoteIdentifier("CATEGORY") : "")
                + " FROM " + dialect.qualifiedTable(table);
        Map<Long, List<String>> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet data = statement.executeQuery(query)) {
            while (data.next()) {
                long key = data.getBigDecimal(1).longValueExact();
                List<String> fields =
                        withCategory ? List.of(data.getString(2), data.getString(3)) : List.of(data.getString(2));
                if (result.put(key, fields) != null) {
                    throw new AssertionError("Duplicate target primary key: " + key);
                }
            }
        }
        return result;
    }

    private static void dropIfPresent(Statement statement, JdbcDialect dialect, TableId table) {
        try {
            statement.execute("DROP TABLE " + dialect.qualifiedTable(table));
        } catch (SQLException ignored) {
            // The fixture owns these uniquely named tables; preserve the original failure.
        }
    }

    private static WriterInitContext context() {
        return new WriterInitContext() {
            @Override
            public TaskInfo getTaskInfo() {
                throw new UnsupportedOperationException("This independent Writer does not need TaskInfo");
            }

            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }
        };
    }
}
