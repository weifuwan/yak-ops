package io.yak.ops.connector.cdc.mysql.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSource;
import io.yak.ops.connector.cdc.mysql.source.MySqlHybridCdcSource;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.SchemaEvolutionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.flow.runtime.execution.EmbeddedJobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/** CDC and JDBC runtime integration, independent of the Task platform. */
@Tag("pipeline")
@Tag("cdc-pipeline")
@Timeout(360)
abstract class MySqlCdcPipelineTestBase {
    @RegisterExtension
    static final MySqlDatabase SOURCE = MySqlDatabase.cdc();

    protected abstract DatabaseMetadata targetDatabase();

    private static final String DATABASE = "yak_cdc_it";
    private static final TableId SOURCE_ORDERS = new TableId(DATABASE, null, "cdc_rt_orders");
    private static final TableId SOURCE_ITEMS = new TableId(DATABASE, null, "cdc_rt_items");
    private static final Map<Long, String> INITIAL_ORDERS = Map.of(1L, "old", 100L, "deleted");
    private static final Map<Long, ItemRow> INITIAL_ITEMS = Map.of(10L, new ItemRow("ten", 2L));
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(120);

    @TempDir
    Path stateDirectory;

    @Test
    void restoresCheckpointAndConvergesMultiTableChangelogAtEachJdbcDestination() throws Exception {

        String password = SOURCE.metadata().password();
        JdbcConnectionOptions sourceOptions =
                new JdbcConnectionOptions(SOURCE.metadata().url(), "root", password);
        JdbcConnectionOptions targetOptions = targetOptions();
        JdbcDialect targetDialect = JdbcDialects.forUrl(targetOptions.url());
        TableSchema orderSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(64).copy(false))),
                List.of("ID"));
        TableSchema itemSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("SKU", LogicalTypes.varchar(64).copy(false)),
                        new Column("QTY", LogicalTypes.BIGINT.copy(false))),
                List.of("ID"));

        try (Connection sourceConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(sourceOptions)
                                .getConnection();
                Connection targetConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(targetOptions)
                                .getConnection()) {
            TableId targetOrders = targetTable(targetConnection, targetOptions.url(), "YF_CDC_RT_ORDERS");
            TableId targetItems = targetTable(targetConnection, targetOptions.url(), "YF_CDC_RT_ITEMS");
            try {
                drop(targetConnection, targetDialect, targetItems);
                drop(targetConnection, targetDialect, targetOrders);
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_orders");
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_items");
                sql(
                        sourceConnection,
                        "CREATE TABLE cdc_rt_orders " + "(ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(64) NOT NULL)");
                sql(
                        sourceConnection,
                        "CREATE TABLE cdc_rt_items "
                                + "(ID BIGINT NOT NULL PRIMARY KEY, SKU VARCHAR(64) NOT NULL, QTY BIGINT NOT NULL)");
                sql(targetConnection, targetDialect.createTableSql(targetOrders, orderSchema));
                sql(targetConnection, targetDialect.createTableSql(targetItems, itemSchema));
                sql(sourceConnection, "INSERT INTO cdc_rt_orders VALUES (1,'old'),(100,'deleted')");
                sql(sourceConnection, "INSERT INTO cdc_rt_items VALUES (10,'ten',2)");

                MySqlHybridCdcSource source = MySqlCdcSource.builder()
                        .hostname(SOURCE.metadata().host())
                        .port(SOURCE.metadata().port())
                        .username("root")
                        .password(password)
                        .serverId(5571)
                        .topicPrefix("yak_cdc_runtime_integration")
                        .table(SOURCE_ORDERS, orderSchema)
                        .table(SOURCE_ITEMS, itemSchema)
                        .buildHybrid(1);
                JdbcSink sink = JdbcSink.builder()
                        .withConnectionOptions(targetOptions)
                        .withTablePlans(List.of(
                                new JdbcTableWritePlan(SOURCE_ORDERS, targetOrders, orderSchema, JdbcWriteMode.UPSERT),
                                new JdbcTableWritePlan(SOURCE_ITEMS, targetItems, itemSchema, JdbcWriteMode.UPSERT)))
                        .withBatchFlushPolicy(new BatchFlushPolicy(1, Duration.ZERO))
                        .build();
                Configuration initial = configuration(false);
                StreamGraph graph = graph(source, sink, initial);
                EmbeddedPipelineExecutor executor = new EmbeddedPipelineExecutor();

                JobClient first = executor.execute(graph, initial).get(30, TimeUnit.SECONDS);
                try {
                    awaitTables(
                            first,
                            targetConnection,
                            targetDialect,
                            targetOrders,
                            INITIAL_ORDERS,
                            targetItems,
                            INITIAL_ITEMS);
                    sql(sourceConnection, "UPDATE cdc_rt_orders SET NAME='changed' WHERE ID=1");
                    sql(sourceConnection, "DELETE FROM cdc_rt_orders WHERE ID=100");
                    sql(sourceConnection, "INSERT INTO cdc_rt_orders VALUES (5,'inserted')");
                    // Debezium can represent primary-key changes as DELETE + INSERT.
                    sql(sourceConnection, "UPDATE cdc_rt_items SET ID=11, SKU='moved', QTY=3 WHERE ID=10");
                    awaitTables(
                            first,
                            targetConnection,
                            targetDialect,
                            targetOrders,
                            Map.of(1L, "changed", 5L, "inserted"),
                            targetItems,
                            Map.of(11L, new ItemRow("moved", 3L)));

                    // Checkpoint covers Sink flush and completed Hybrid Snapshot/Binlog progress.
                    assertTrue(((EmbeddedJobClient) first)
                                    .checkpoint()
                                    .get(30, TimeUnit.SECONDS)
                                    .checkpointId()
                            > 0);
                } finally {
                    cancelIfActive(first);
                }

                Configuration restore = configuration(true);
                JobClient second = executor.execute(graph, restore).get(30, TimeUnit.SECONDS);
                try {
                    sql(sourceConnection, "UPDATE cdc_rt_orders SET NAME='after-restart' WHERE ID=1");
                    sql(sourceConnection, "DELETE FROM cdc_rt_items WHERE ID=11");
                    sql(sourceConnection, "INSERT INTO cdc_rt_items VALUES (20,'new',4)");
                    awaitTables(
                            second,
                            targetConnection,
                            targetDialect,
                            targetOrders,
                            Map.of(1L, "after-restart", 5L, "inserted"),
                            targetItems,
                            Map.of(20L, new ItemRow("new", 4L)));
                    assertEquals(JobStatus.RUNNING, second.getJobStatus().get(10, TimeUnit.SECONDS));
                } finally {
                    cancelIfActive(second);
                }
            } finally {
                drop(targetConnection, targetDialect, targetItems);
                drop(targetConnection, targetDialect, targetOrders);
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_items");
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_orders");
            }
        }
    }

    @Test
    void evolvesExistingTableAndRecoversSchemaWithBinlogCheckpoint() throws Exception {
        JdbcConnectionOptions sourceOptions =
                new JdbcConnectionOptions(SOURCE.metadata().url(), "root", SOURCE.metadata().password());
        JdbcConnectionOptions targetOptions = targetOptions();
        JdbcDialect targetDialect = JdbcDialects.forUrl(targetOptions.url());
        String suffix = targetOptions.url().startsWith("jdbc:oracle:")
                ? "oracle"
                : targetOptions.url().startsWith("jdbc:postgresql:") ? "postgres" : "mysql";
        String tableName = "cdc_rt_schema_orders_" + suffix;
        TableId from = new TableId(DATABASE, null, tableName);
        TableSchema initialSchema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("NAME", LogicalTypes.varchar(64).copy(false))),
                List.of("ID"));

        try (Connection sourceConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(sourceOptions)
                                .getConnection();
                Connection targetConnection =
                        new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(targetOptions)
                                .getConnection()) {
            TableId to = targetTable(targetConnection, targetOptions.url(), "YF_CDC_RT_SCHEMA_ORDERS_" + suffix.toUpperCase(java.util.Locale.ROOT));
            try {
                drop(targetConnection, targetDialect, to);
                sql(sourceConnection, "DROP TABLE IF EXISTS " + tableName);
                sql(sourceConnection,
                        "CREATE TABLE " + tableName + " (ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(64) NOT NULL)");
                sql(targetConnection, targetDialect.createTableSql(to, initialSchema));
                sql(sourceConnection, "INSERT INTO " + tableName + " VALUES (1,'before')");

                TableMetrics sourceMetrics = new TableMetrics();
                MySqlHybridCdcSource source = MySqlCdcSource.builder()
                        .hostname(SOURCE.metadata().host())
                        .port(SOURCE.metadata().port())
                        .username("root")
                        .password(SOURCE.metadata().password())
                        .serverId(suffix.equals("mysql") ? 5581 : suffix.equals("oracle") ? 5601 : 5621)
                        .topicPrefix("yak_cdc_runtime_schema_evolution_" + suffix)
                        .table(from, initialSchema)
                        .buildHybrid(1, sourceMetrics);
                JdbcSink sink = JdbcSink.builder()
                        .withConnectionOptions(targetOptions)
                        .withTablePlans(List.of(new JdbcTableWritePlan(from, to, initialSchema, JdbcWriteMode.UPSERT)))
                        .withBatchFlushPolicy(new BatchFlushPolicy(1, Duration.ZERO))
                        .build();
                Configuration initial = configuration(false).set(SchemaEvolutionOptions.ENABLED, true);
                StreamGraph graph = graph(source, sink, initial);
                EmbeddedPipelineExecutor executor = new EmbeddedPipelineExecutor();

                JobClient first = executor.execute(graph, initial).get(30, TimeUnit.SECONDS);
                try {
                    awaitBaseSchemaRow(first, targetConnection, targetDialect, to, "before");
                    // A delivered snapshot row does not imply that the CDC Binlog gate is open.
                    // Verify one genuine incremental mutation before testing a runtime DDL.
                    awaitStreamingPhase(first, sourceMetrics);
                    sql(sourceConnection, "UPDATE " + tableName + " SET NAME='stream-ready' WHERE ID=1");
                    awaitIncrementalRow(first, sourceMetrics, from, targetConnection, targetDialect, to, "stream-ready");
                    sql(sourceConnection, "ALTER TABLE " + tableName + " ADD COLUMN NOTE VARCHAR(128) NULL");
                    if (targetOptions.url().startsWith("jdbc:oracle:")) {
                        // A row after the DDL also exercises reconciliation when Debezium
                        // produces no structured tableChanges for this ALTER.
                        // Oracle must reject unsupported DDL without delivering that row.
                        sql(sourceConnection, "UPDATE " + tableName + " SET NAME='after', NOTE='blocked' WHERE ID=1");
                        awaitUnsupportedSchemaChange(first);
                        assertEquals("stream-ready", orderRows(targetConnection, targetDialect, to).get(1L));
                        return;
                    }
                    sql(sourceConnection, "UPDATE " + tableName + " SET NAME='after', NOTE='added' WHERE ID=1");
                    awaitEvolvedRows(first, targetConnection, targetDialect, to, Map.of(1L, "added"));
                    assertTrue(((EmbeddedJobClient) first)
                                    .checkpoint()
                                    .get(30, TimeUnit.SECONDS)
                                    .checkpointId()
                            > 0);
                } finally {
                    cancelIfActive(first);
                }

                Configuration restore = configuration(true).set(SchemaEvolutionOptions.ENABLED, true);
                JobClient second = executor.execute(graph, restore).get(30, TimeUnit.SECONDS);
                try {
                    sql(sourceConnection,
                            "INSERT INTO " + tableName + " (ID,NAME,NOTE) VALUES (2,'new','restored')");
                    awaitEvolvedRows(
                            second, targetConnection, targetDialect, to, Map.of(1L, "added", 2L, "restored"));
                    // Recovery must retain enough Debezium history to process the NEXT DDL.
                    // A stale bootstrap history can appear correct until the first row after restart.
                    sql(sourceConnection, "ALTER TABLE " + tableName + " ADD COLUMN EXTRA VARCHAR(64) NULL");
                    sql(sourceConnection, "UPDATE " + tableName + " SET EXTRA='after-restart' WHERE ID=2");
                    awaitColumnValues(second, targetConnection, targetDialect, to,
                            "EXTRA", java.util.Collections.singletonMap(2L, "after-restart"));
                    assertEquals(JobStatus.RUNNING, second.getJobStatus().get(10, TimeUnit.SECONDS));
                } finally {
                    cancelIfActive(second);
                }
            } finally {
                drop(targetConnection, targetDialect, to);
                sql(sourceConnection, "DROP TABLE IF EXISTS " + tableName);
            }
        }
    }

    private static void awaitUnsupportedSchemaChange(JobClient job) throws Exception {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            JobStatus status = job.getJobStatus().get(5, TimeUnit.SECONDS);
            if (status == JobStatus.FAILED) {
                try {
                    job.getJobExecutionResult().get(5, TimeUnit.SECONDS);
                    throw new AssertionError("Unsupported JDBC schema change unexpectedly succeeded");
                } catch (java.util.concurrent.ExecutionException failure) {
                    for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
                        if (cause.getMessage() != null
                                && cause.getMessage().contains("does not support this schema change")) {
                            return;
                        }
                    }
                    throw new AssertionError("CDC failed for a reason other than unsupported Oracle DDL", failure);
                }
            }
            if (status.isTerminalState()) {
                throw new AssertionError("CDC did not fail closed for unsupported Oracle DDL: " + status);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Unsupported Oracle DDL did not stop CDC before the deadline");
    }

    private static void awaitStreamingPhase(JobClient job, TableMetrics metrics) throws Exception {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ensureRunning(job);
            if (metrics.continuousSnapshot().phase() == TableMetrics.Phase.STREAMING) return;
            Thread.sleep(100);
        }
        throw new AssertionError("CDC snapshot handoff did not enter STREAMING");
    }

    private static void awaitIncrementalRow(
            JobClient job,
            TableMetrics metrics,
            TableId sourceTable,
            Connection connection,
            JdbcDialect dialect,
            TableId target,
            String expected) throws Exception {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ensureRunning(job);
            TableMetrics.Snapshot incremental = metrics.continuousSnapshot().incrementalTables().get(sourceTable);
            if (incremental != null
                    && incremental.readRows() > 0
                    && expected.equals(orderRows(connection, dialect, target).get(1L))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("CDC Binlog mutation was not delivered after snapshot handoff");
    }

    private static void awaitBaseSchemaRow(
            JobClient job, Connection connection, JdbcDialect dialect, TableId table, String expected)
            throws Exception {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ensureRunning(job);
            Map<Long, String> rows = orderRows(connection, dialect, table);
            if (expected.equals(rows.get(1L))) return;
            Thread.sleep(100);
        }
        throw new AssertionError("CDC initial table did not converge before schema evolution");
    }

    private static void awaitEvolvedRows(
            JobClient job, Connection connection, JdbcDialect dialect, TableId table, Map<Long, String> expected)
            throws Exception {
        awaitColumnValues(job, connection, dialect, table, "NOTE", expected);
    }

    private static void awaitColumnValues(
            JobClient job, Connection connection, JdbcDialect dialect, TableId table,
            String column, Map<Long, String> expected) throws Exception {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            ensureRunning(job);
            try (Statement sql = connection.createStatement();
                    ResultSet rows = sql.executeQuery("SELECT "
                            + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier(column)
                            + " FROM " + dialect.qualifiedTable(table))) {
                Map<Long, String> actual = new LinkedHashMap<>();
                while (rows.next()) {
                    String value = rows.getString(2);
                    if (value != null) actual.put(rows.getLong(1), value);
                }
                if (expected.equals(actual)) return;
            } catch (SQLException notMigratedYet) {
                // The target column may not exist until MetadataApplier completes its DDL.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("CDC schema evolution did not converge before deadline");
    }

    private static void ensureRunning(JobClient job) throws Exception {
        JobStatus status = job.getJobStatus().get(5, TimeUnit.SECONDS);
        // The worker starts asynchronously: CREATED can briefly precede RUNNING.
        if (status == JobStatus.FAILED) {
            try {
                job.getJobExecutionResult().get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException failure) {
                throw new AssertionError("CDC pipeline failed during schema evolution", failure.getCause());
            }
        }
        if (status != JobStatus.CREATED && status != JobStatus.RUNNING) {
            throw new AssertionError("CDC pipeline stopped during schema evolution: " + status);
        }
    }

    private static void cancelIfActive(JobClient job) throws Exception {
        if (job.getJobStatus().get(5, TimeUnit.SECONDS).isTerminalState()) {
            return;
        }
        try {
            job.cancel().get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException failure) {
            // The job may terminate between the state read and the cancellation request.
            // Do not replace the original assertion or job failure with a cleanup exception.
            if (!job.getJobStatus().get(5, TimeUnit.SECONDS).isTerminalState()) {
                throw failure;
            }
        }
    }

    private Configuration configuration(boolean restore) {
        return new Configuration()
                .set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(10))
                .set(CheckpointingOptions.STATE_DIRECTORY, stateDirectory.toString())
                .set(CheckpointingOptions.RESTORE_LATEST, restore);
    }

    private static StreamGraph graph(MySqlHybridCdcSource source, JdbcSink sink, Configuration config) {
        SourceTransformation<TableRecord> input =
                new SourceTransformation<>("mysql-cdc-source", source, TableRecord.class, 2);
        SinkTransformation<TableRecord> output = new SinkTransformation<>(input, "jdbc-upsert-sink", sink, 1);
        input.setUid("mysql-cdc-runtime-integration-source");
        output.setUid("mysql-cdc-runtime-integration-sink");
        return new StreamGraphGenerator(output, config).generate();
    }

    private JdbcConnectionOptions targetOptions() {
        DatabaseMetadata target = targetDatabase();
        return new JdbcConnectionOptions(target.url(), target.username(), target.password());
    }

    private static TableId targetTable(Connection connection, String url, String name) throws SQLException {
        boolean mysql = url.startsWith("jdbc:mysql:");
        return new TableId(mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), name);
    }

    private static void awaitTables(
            JobClient job,
            Connection connection,
            JdbcDialect dialect,
            TableId orders,
            Map<Long, String> expectedOrders,
            TableId items,
            Map<Long, ItemRow> expectedItems)
            throws Exception {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        Map<Long, String> actualOrders = Map.of();
        Map<Long, ItemRow> actualItems = Map.of();
        while (System.nanoTime() < deadline) {
            JobStatus status = job.getJobStatus().get(5, TimeUnit.SECONDS);
            if (status == JobStatus.FAILED || status == JobStatus.CANCELED || status == JobStatus.FINISHED) {
                throw new AssertionError("CDC job stopped before convergence: " + status);
            }
            actualOrders = orderRows(connection, dialect, orders);
            actualItems = itemRows(connection, dialect, items);
            if (expectedOrders.equals(actualOrders) && expectedItems.equals(actualItems)) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(expectedOrders, actualOrders, "Timed out waiting for CDC orders convergence");
        assertEquals(expectedItems, actualItems, "Timed out waiting for CDC items convergence");
    }

    private static Map<Long, String> orderRows(Connection connection, JdbcDialect dialect, TableId table)
            throws SQLException {
        Map<Long, String> records = new LinkedHashMap<>();
        String sql = "SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("NAME") + " FROM "
                + dialect.qualifiedTable(table);
        try (Statement statement = connection.createStatement();
                ResultSet data = statement.executeQuery(sql)) {
            while (data.next()) {
                long key = data.getLong(1);
                String previous = records.put(key, data.getString(2));
                if (previous != null) {
                    throw new AssertionError("Duplicate target primary key: " + key);
                }
            }
        }
        return records;
    }

    private static Map<Long, ItemRow> itemRows(Connection connection, JdbcDialect dialect, TableId table)
            throws SQLException {
        Map<Long, ItemRow> records = new LinkedHashMap<>();
        String query = "SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("SKU") + ", "
                + dialect.quoteIdentifier("QTY") + " FROM " + dialect.qualifiedTable(table);
        try (Statement statement = connection.createStatement();
                ResultSet data = statement.executeQuery(query)) {
            while (data.next()) {
                long key = data.getLong(1);
                ItemRow previous = records.put(key, new ItemRow(data.getString(2), data.getLong(3)));
                if (previous != null) {
                    throw new AssertionError("Duplicate target primary key: " + key);
                }
            }
        }
        return records;
    }

    private record ItemRow(String sku, long quantity) {}

    private static void sql(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

    private static void drop(Connection connection, JdbcDialect dialect, TableId table) {
        try {
            sql(connection, "DROP TABLE " + dialect.qualifiedTable(table));
        } catch (SQLException ignored) {
            // Tables belong to disposable CI databases; preserve the original test failure.
        }
    }
}
