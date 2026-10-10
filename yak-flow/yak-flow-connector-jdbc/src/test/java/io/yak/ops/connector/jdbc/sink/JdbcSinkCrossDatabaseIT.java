package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialectConverter;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Cross-vendor MySQL/PostgreSQL/Oracle acceptance against two disposable live databases.
 *
 * <p>The bounded JDBC Source and multi-table JDBC Sink run through the actual YakFlow
 * StreamGraph/ExecutionGraph. A post-run Changelog verifies target-driver UPSERT/DELETE
 * behavior and at-least-once replay. The test is never silently skipped when configured.
 */
class JdbcSinkCrossDatabaseIT {

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

    @Test
    void transfersMultipleTablesAndAppliesChangelogAcrossDatabaseVendors() throws Exception {
        if (!Boolean.getBoolean("jdbc.it.allow-write")) {
            throw new IllegalStateException("Real JDBC Sink acceptance requires -Djdbc.it.allow-write=true");
        }
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

        try (Connection sourceConnection = sourceOptions.openConnection();
                Connection targetConnection = targetOptions.openConnection();
                Statement sourceDdl = sourceConnection.createStatement();
                Statement targetDdl = targetConnection.createStatement()) {
            TableId sourceA = tableId(sourceConnection, sourceOptions.url(), "YF_SINK_SRC_A");
            TableId sourceB = tableId(sourceConnection, sourceOptions.url(), "YF_SINK_SRC_B");
            TableId targetA = tableId(targetConnection, targetOptions.url(), "YF_SINK_DST_A");
            TableId targetB = tableId(targetConnection, targetOptions.url(), "YF_SINK_DST_B");
            try {
                sourceDdl.execute(sourceDialect.createTableSql(sourceA, sourceSchema));
                sourceDdl.execute(sourceDialect.createTableSql(sourceB, sourceSchema));
                targetDdl.execute(targetDialect.createTableSql(targetA, targetSchema));
                targetDdl.execute(targetDialect.createTableSql(targetB, targetSchema));
                insertRows(sourceConnection, sourceDialect, sourceSchema, sourceA, 1L, 2L, 3L);
                insertRows(sourceConnection, sourceDialect, sourceSchema, sourceB, 4L, 5L);

                JdbcSink sink = JdbcSink.builder()
                        .withConnectionOptions(targetOptions)
                        .withTablePlans(List.of(
                                new JdbcTableWritePlan(
                                        sourceA, targetA, targetSchema, JdbcWriteMode.UPSERT,
                                        sourceSchema, Map.of("MESSAGE", "LABEL")),
                                new JdbcTableWritePlan(
                                        sourceB, targetB, targetSchema, JdbcWriteMode.UPSERT,
                                        sourceSchema, Map.of("MESSAGE", "LABEL"))))
                        .withBatchFlushPolicy(new BatchFlushPolicy(3, Duration.ZERO))
                        .build();

                JdbcSource source = new JdbcSource(sourceOptions, List.of(sourceA, sourceB));
                SourceTransformation<TableRecord> sourceNode =
                        new SourceTransformation<>("real-cross-database-source", source, TableRecord.class, 1);
                SinkTransformation<TableRecord> sinkNode =
                        new SinkTransformation<>(sourceNode, "real-cross-database-sink", sink, 1);
                StreamGraph graph = new StreamGraphGenerator(sinkNode, new Configuration()).generate();
                JobClient job = new EmbeddedPipelineExecutor()
                        .execute(graph, new Configuration())
                        .get(30, TimeUnit.SECONDS);
                job.getJobExecutionResult().get(120, TimeUnit.SECONDS);
                assertEquals(JobStatus.FINISHED, job.getJobStatus().get(10, TimeUnit.SECONDS));
                assertEquals(3L, countRows(targetConnection, targetDialect, targetA));
                assertEquals(2L, countRows(targetConnection, targetDialect, targetB));

                // UPDATE_BEFORE deletes the old key and UPDATE_AFTER upserts the new key.
                try (JdbcWriter writer = sink.createWriter(context())) {
                    writer.write(
                            record(sourceA, RowKind.UPDATE_BEFORE, 1L, "row-1"), RECORD_CONTEXT);
                    writer.write(
                            record(sourceA, RowKind.UPDATE_AFTER, 101L, "renamed"), RECORD_CONTEXT);
                    writer.write(
                            record(sourceB, RowKind.DELETE, 4L, null), RECORD_CONTEXT);
                    writer.flush(false);
                }
                assertEquals(3L, countRows(targetConnection, targetDialect, targetA));
                assertEquals(1L, countRows(targetConnection, targetDialect, targetB));
                assertEquals(0L, countKey(targetConnection, targetDialect, targetA, 1L));
                assertEquals(1L, countKey(targetConnection, targetDialect, targetA, 101L));
                assertEquals(0L, countKey(targetConnection, targetDialect, targetB, 4L));

                // Replaying committed records after a completed checkpoint is at-least-once.
                try (JdbcWriter replay = sink.createWriter(context())) {
                    replay.write(record(sourceA, RowKind.INSERT, 101L, "renamed"), RECORD_CONTEXT);
                    replay.write(record(sourceB, RowKind.DELETE, 4L, null), RECORD_CONTEXT);
                    replay.flush(false);
                }
                assertEquals(3L, countRows(targetConnection, targetDialect, targetA));
                assertEquals(1L, countRows(targetConnection, targetDialect, targetB));
                assertTrue(countKey(targetConnection, targetDialect, targetA, 101L) == 1L);
            } finally {
                dropIfPresent(targetDdl, targetDialect, targetB);
                dropIfPresent(targetDdl, targetDialect, targetA);
                dropIfPresent(sourceDdl, sourceDialect, sourceB);
                dropIfPresent(sourceDdl, sourceDialect, sourceA);
            }
        }
    }

    private static JdbcConnectionOptions connectionOptions(String role) {
        String prefix = "jdbc.it." + role + ".";
        String url = System.getProperty(prefix + "url");
        String user = System.getProperty(prefix + "user");
        if (url == null || url.isBlank() || user == null || user.isBlank()) {
            throw new IllegalStateException("Missing " + prefix + "url / user for real cross-database acceptance");
        }
        return new JdbcConnectionOptions(url, user, System.getProperty(prefix + "password", ""));
    }

    private static TableId tableId(Connection connection, String url, String table) throws SQLException {
        boolean mysql = url.startsWith("jdbc:mysql:");
        return new TableId(
                mysql ? connection.getCatalog() : null,
                mysql ? null : connection.getSchema(),
                table);
    }

    private static void insertRows(
            Connection connection, JdbcDialect dialect, TableSchema schema, TableId table, long... keys)
            throws SQLException {
        JdbcDialectConverter converter = dialect.createRowConverter(schema);
        try (PreparedStatement statement = connection.prepareStatement(dialect.insertSql(table, schema))) {
            for (long key : keys) {
                converter.toExternal(
                        GenericRowData.of(BigDecimal.valueOf(key), "row-" + key), statement);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static TableRecord record(TableId table, RowKind kind, long key, String label) {
        return new TableRecord(table, kind, GenericRowData.of(BigDecimal.valueOf(key), label));
    }

    private static long countRows(Connection connection, JdbcDialect dialect, TableId table) throws SQLException {
        try (Statement statement = connection.createStatement();
                var results = statement.executeQuery("SELECT COUNT(*) FROM " + dialect.qualifiedTable(table))) {
            results.next();
            return results.getLong(1);
        }
    }

    private static long countKey(Connection connection, JdbcDialect dialect, TableId table, long key)
            throws SQLException {
        String query = "SELECT COUNT(*) FROM " + dialect.qualifiedTable(table)
                + " WHERE " + dialect.quoteIdentifier("ID") + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setBigDecimal(1, BigDecimal.valueOf(key));
            try (var results = statement.executeQuery()) {
                results.next();
                return results.getLong(1);
            }
        }
    }

    private static void dropIfPresent(Statement statement, JdbcDialect dialect, TableId table) {
        try {
            statement.execute("DROP TABLE " + dialect.qualifiedTable(table));
        } catch (SQLException ignored) {
            // Acceptance always uses disposable databases. Preserve the original test failure.
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
