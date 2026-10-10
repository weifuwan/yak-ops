package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcTableWritePlan;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.CheckpointingOptions;
import io.yak.ops.core.configuration.Configuration;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in engine-level MySQL Hybrid CDC to JDBC Sink acceptance against disposable databases.
 *
 * <p>Runs two heterogeneous table schemas through the real StreamGraph, parallel Snapshot
 * readers, one ordered JDBC Writer, durable checkpoints and a recovered execution attempt. It does not start Business
 * tasks, verify Schema Evolution, or claim exactly-once delivery.
 */
class MySqlCdcRuntimeCrossDatabaseIT {

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
        if (!Boolean.getBoolean("mysql.cdc.runtime.it.enabled")) {
            throw new IllegalStateException("Set -Dmysql.cdc.runtime.it.enabled=true for real CDC Runtime IT");
        }

        String password = System.getProperty("mysql.cdc.it.password", "rootpass");
        JdbcConnectionOptions sourceOptions = new JdbcConnectionOptions(
                "jdbc:mysql://127.0.0.1:3306/" + DATABASE + "?useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                password);
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

        try (Connection sourceConnection = sourceOptions.openConnection();
                Connection targetConnection = targetOptions.openConnection()) {
            TableId targetOrders = targetTable(targetConnection, targetOptions.url(), "YF_CDC_RT_ORDERS");
            TableId targetItems = targetTable(targetConnection, targetOptions.url(), "YF_CDC_RT_ITEMS");
            try {
                drop(targetConnection, targetDialect, targetItems);
                drop(targetConnection, targetDialect, targetOrders);
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_orders");
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_items");
                sql(sourceConnection, "CREATE TABLE cdc_rt_orders (ID BIGINT NOT NULL PRIMARY KEY, NAME VARCHAR(64) NOT NULL)");
                sql(sourceConnection, "CREATE TABLE cdc_rt_items "
                        + "(ID BIGINT NOT NULL PRIMARY KEY, SKU VARCHAR(64) NOT NULL, QTY BIGINT NOT NULL)");
                sql(targetConnection, targetDialect.createTableSql(targetOrders, orderSchema));
                sql(targetConnection, targetDialect.createTableSql(targetItems, itemSchema));
                sql(sourceConnection, "INSERT INTO cdc_rt_orders VALUES (1,'old'),(100,'deleted')");
                sql(sourceConnection, "INSERT INTO cdc_rt_items VALUES (10,'ten',2)");

                MySqlHybridCdcSource source = MySqlCdcSource.builder()
                        .hostname("127.0.0.1")
                        .username("root")
                        .password(password)
                        .serverId(5571)
                        .topicPrefix("yak_cdc_runtime_acceptance")
                        .table(SOURCE_ORDERS, orderSchema)
                        .table(SOURCE_ITEMS, itemSchema)
                        .buildHybrid(1);
                JdbcSink sink = JdbcSink.builder()
                        .withConnectionOptions(targetOptions)
                        .withTablePlans(List.of(
                                new JdbcTableWritePlan(
                                        SOURCE_ORDERS, targetOrders, orderSchema, JdbcWriteMode.UPSERT),
                                new JdbcTableWritePlan(
                                        SOURCE_ITEMS, targetItems, itemSchema, JdbcWriteMode.UPSERT)))
                        .withBatchFlushPolicy(new BatchFlushPolicy(1, Duration.ZERO))
                        .build();
                Configuration initial = configuration(false);
                StreamGraph graph = graph(source, sink, initial);
                EmbeddedPipelineExecutor executor = new EmbeddedPipelineExecutor();

                JobClient first = executor.execute(graph, initial).get(30, TimeUnit.SECONDS);
                try {
                    awaitTables(first, targetConnection, targetDialect, targetOrders, INITIAL_ORDERS,
                            targetItems, INITIAL_ITEMS);
                    sql(sourceConnection, "UPDATE cdc_rt_orders SET NAME='changed' WHERE ID=1");
                    sql(sourceConnection, "DELETE FROM cdc_rt_orders WHERE ID=100");
                    sql(sourceConnection, "INSERT INTO cdc_rt_orders VALUES (5,'inserted')");
                    // Debezium can represent primary-key changes as DELETE + INSERT.
                    sql(sourceConnection, "UPDATE cdc_rt_items SET ID=11, SKU='moved', QTY=3 WHERE ID=10");
                    awaitTables(first, targetConnection, targetDialect, targetOrders,
                            Map.of(1L, "changed", 5L, "inserted"),
                            targetItems, Map.of(11L, new ItemRow("moved", 3L)));

                    // Checkpoint covers Sink flush and completed Hybrid Snapshot/Binlog progress.
                    assertTrue(((EmbeddedJobClient) first).checkpoint().get(30, TimeUnit.SECONDS)
                            .checkpointId() > 0);
                } finally {
                    first.cancel().get(30, TimeUnit.SECONDS);
                }

                Configuration restore = configuration(true);
                JobClient second = executor.execute(graph, restore).get(30, TimeUnit.SECONDS);
                try {
                    sql(sourceConnection, "UPDATE cdc_rt_orders SET NAME='after-restart' WHERE ID=1");
                    sql(sourceConnection, "DELETE FROM cdc_rt_items WHERE ID=11");
                    sql(sourceConnection, "INSERT INTO cdc_rt_items VALUES (20,'new',4)");
                    awaitTables(second, targetConnection, targetDialect, targetOrders,
                            Map.of(1L, "after-restart", 5L, "inserted"),
                            targetItems, Map.of(20L, new ItemRow("new", 4L)));
                    assertEquals(JobStatus.RUNNING, second.getJobStatus().get(10, TimeUnit.SECONDS));
                } finally {
                    second.cancel().get(30, TimeUnit.SECONDS);
                }
            } finally {
                drop(targetConnection, targetDialect, targetItems);
                drop(targetConnection, targetDialect, targetOrders);
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_items");
                sql(sourceConnection, "DROP TABLE IF EXISTS cdc_rt_orders");
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
        SinkTransformation<TableRecord> output =
                new SinkTransformation<>(input, "jdbc-upsert-sink", sink, 1);
        input.setUid("mysql-cdc-runtime-acceptance-source");
        output.setUid("mysql-cdc-runtime-acceptance-sink");
        return new StreamGraphGenerator(output, config).generate();
    }

    private static JdbcConnectionOptions targetOptions() {
        String url = System.getProperty("jdbc.it.target.url");
        String user = System.getProperty("jdbc.it.target.user");
        if (url == null || url.isBlank() || user == null || user.isBlank()) {
            throw new IllegalStateException("Real target requires jdbc.it.target.url and jdbc.it.target.user");
        }
        return new JdbcConnectionOptions(url, user, System.getProperty("jdbc.it.target.password", ""));
    }

    private static TableId targetTable(Connection connection, String url, String name) throws SQLException {
        boolean mysql = url.startsWith("jdbc:mysql:");
        return new TableId(mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), name);
    }

    private static void awaitTables(
            JobClient job, Connection connection, JdbcDialect dialect,
            TableId orders, Map<Long, String> expectedOrders,
            TableId items, Map<Long, ItemRow> expectedItems) throws Exception {
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
        String sql = "SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("NAME")
                + " FROM " + dialect.qualifiedTable(table);
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
        String query = "SELECT " + dialect.quoteIdentifier("ID") + ", " + dialect.quoteIdentifier("SKU")
                + ", " + dialect.quoteIdentifier("QTY") + " FROM " + dialect.qualifiedTable(table);
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
