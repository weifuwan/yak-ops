package io.yak.ops.flow.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.trace.RuntimeTraceEvent;
import io.yak.ops.flow.api.trace.RuntimeTraceListener;
import io.yak.ops.flow.connector.jdbc.sink.JdbcSink;
import io.yak.ops.flow.connector.jdbc.source.JdbcSource;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSinkBatchTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSinkOpenedTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcSourceSplitTraceEvent;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceEventType;
import io.yak.ops.flow.connector.jdbc.trace.JdbcTraceFailureStage;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProvider;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.sql.DriverManager;
import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class JdbcRuntimeTraceTest {

    private static final String DRIVER = "org.h2.Driver";
    private static final JdbcConnectionProvider DIRECT_CONNECTION = (connection, timeoutSeconds) -> {
        Class.forName(connection.driverClassName());
        Properties properties = new Properties();
        properties.putAll(connection.properties());
        properties.setProperty("user", connection.username());
        if (connection.password() != null) {
            properties.setProperty("password", connection.password());
        }
        return DriverManager.getConnection(connection.jdbcUrl(), properties);
    };

    @Test
    void shouldEmitSplitAndBatchDiagnosticsWithoutRowPayload() throws Exception {
        TestDataSourceConnection sourceConnection =
                connection("jdbc:h2:mem:trace_source;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDataSourceConnection targetConnection =
                connection("jdbc:h2:mem:trace_target;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        createRangeSource(sourceConnection);
        createTarget(targetConnection);

        CopyOnWriteArrayList<RuntimeTraceEvent> events = new CopyOnWriteArrayList<>();
        RuntimeTraceListener listener = events::add;
        YakTableSchema schema = sourceSchema();
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        sourceConnection,
                        new DataSourceTablePath(null, null, "source_table"),
                        schema,
                        2,
                        2,
                        5,
                        new JdbcNumericSplitConfig("id", 1, 10, 3)),
                DIRECT_CONNECTION,
                listener);
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        new DataSourceTablePath(null, null, "target_table"),
                        2,
                        5),
                DIRECT_CONNECTION,
                listener);

        assertEquals(
                ExecutionStatus.SUCCEEDED,
                new LocalExecutionEngine().start(source, sink, schema, 3).await(Duration.ofSeconds(5)));

        List<JdbcSourceSplitTraceEvent> sourceEvents = events.stream()
                .filter(JdbcSourceSplitTraceEvent.class::isInstance)
                .map(JdbcSourceSplitTraceEvent.class::cast)
                .toList();
        assertEquals(
                3,
                sourceEvents.stream()
                        .filter(event -> event.eventType() == JdbcTraceEventType.SOURCE_SPLIT_PLANNED)
                        .count());
        assertEquals(
                3,
                sourceEvents.stream()
                        .filter(event -> event.eventType() == JdbcTraceEventType.SOURCE_SPLIT_STARTED)
                        .count());
        List<JdbcSourceSplitTraceEvent> finishedSplits = sourceEvents.stream()
                .filter(event -> event.eventType() == JdbcTraceEventType.SOURCE_SPLIT_FINISHED)
                .toList();
        assertEquals(3, finishedSplits.size());
        assertEquals(10L, finishedSplits.stream().mapToLong(JdbcSourceSplitTraceEvent::rows).sum());
        assertTrue(finishedSplits.stream().allMatch(event -> event.durationMillis() >= 0));
        assertTrue(sourceEvents.stream()
                .filter(event -> event.eventType() == JdbcTraceEventType.SOURCE_SPLIT_PLANNED)
                .allMatch(event -> event.sql() != null
                        && event.sql().contains(" WHERE ")
                        && event.parameters().size() == 2));

        JdbcSinkOpenedTraceEvent opened = events.stream()
                .filter(JdbcSinkOpenedTraceEvent.class::isInstance)
                .map(JdbcSinkOpenedTraceEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(2, opened.batchSize());
        assertEquals("INSERT", opened.writeMode());
        assertTrue(opened.sql().startsWith("INSERT INTO"));

        List<JdbcSinkBatchTraceEvent> committedBatches = events.stream()
                .filter(JdbcSinkBatchTraceEvent.class::isInstance)
                .map(JdbcSinkBatchTraceEvent.class::cast)
                .filter(event -> event.eventType() == JdbcTraceEventType.SINK_BATCH_COMMITTED)
                .toList();
        assertEquals(5, committedBatches.size());
        assertEquals(10L, committedBatches.stream().mapToLong(JdbcSinkBatchTraceEvent::rows).sum());
        assertTrue(committedBatches.stream().allMatch(event -> event.executeDurationMillis() >= 0));
        assertTrue(committedBatches.stream().allMatch(event -> event.commitDurationMillis() >= 0));
    }

    @Test
    void shouldEmitSourceFailureWithStageAndErrorContext() throws Exception {
        TestDataSourceConnection sourceConnection =
                connection("jdbc:h2:mem:trace_missing_source;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDataSourceConnection targetConnection =
                connection("jdbc:h2:mem:trace_missing_target;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        createTarget(targetConnection);

        CopyOnWriteArrayList<RuntimeTraceEvent> events = new CopyOnWriteArrayList<>();
        RuntimeTraceListener listener = events::add;
        YakTableSchema schema = sourceSchema();
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        sourceConnection,
                        new DataSourceTablePath(null, null, "missing_source"),
                        schema,
                        2,
                        2,
                        5,
                        new JdbcNumericSplitConfig("id", 1, 10, 1)),
                DIRECT_CONNECTION,
                listener);
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        new DataSourceTablePath(null, null, "target_table"),
                        2,
                        5),
                DIRECT_CONNECTION,
                listener);

        assertEquals(
                ExecutionStatus.FAILED,
                new LocalExecutionEngine().start(source, sink, schema).await(Duration.ofSeconds(5)));

        JdbcSourceSplitTraceEvent failed = events.stream()
                .filter(JdbcSourceSplitTraceEvent.class::isInstance)
                .map(JdbcSourceSplitTraceEvent.class::cast)
                .filter(event -> event.eventType() == JdbcTraceEventType.SOURCE_SPLIT_FAILED)
                .findFirst()
                .orElseThrow();
        assertEquals(JdbcTraceFailureStage.SOURCE_OPEN, failed.failureStage());
        assertEquals(0L, failed.rows());
        assertNotNull(failed.errorType());
        assertNotNull(failed.errorMessage());
    }

    @Test
    void shouldEmitSinkBatchFailureWithBatchScope() throws Exception {
        TestDataSourceConnection sourceConnection =
                connection("jdbc:h2:mem:trace_sink_fail_source;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDataSourceConnection targetConnection =
                connection("jdbc:h2:mem:trace_sink_fail_target;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        createSource(sourceConnection);
        createTarget(targetConnection);
        try (var connection = DIRECT_CONNECTION.open(targetConnection, 5);
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO target_table VALUES (1, 'existing', 1.00)");
        }

        CopyOnWriteArrayList<RuntimeTraceEvent> events = new CopyOnWriteArrayList<>();
        RuntimeTraceListener listener = events::add;
        YakTableSchema schema = sourceSchema();
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        sourceConnection,
                        new DataSourceTablePath(null, null, "source_table"),
                        schema,
                        2,
                        2,
                        5),
                DIRECT_CONNECTION,
                listener);
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        new DataSourceTablePath(null, null, "target_table"),
                        2,
                        5),
                DIRECT_CONNECTION,
                listener);

        assertEquals(
                ExecutionStatus.FAILED,
                new LocalExecutionEngine().start(source, sink, schema).await(Duration.ofSeconds(5)));

        JdbcSinkBatchTraceEvent failed = events.stream()
                .filter(JdbcSinkBatchTraceEvent.class::isInstance)
                .map(JdbcSinkBatchTraceEvent.class::cast)
                .filter(event -> event.eventType() == JdbcTraceEventType.SINK_BATCH_FAILED)
                .findFirst()
                .orElseThrow();
        assertEquals(1L, failed.batchNo());
        assertEquals(2L, failed.rows());
        assertEquals(JdbcTraceFailureStage.SINK_WRITE, failed.failureStage());
        assertNotNull(failed.errorType());
        assertNotNull(failed.errorMessage());
    }

    @Test
    void traceListenerFailureMustNotFailDataFlow() throws Exception {
        TestDataSourceConnection sourceConnection =
                connection("jdbc:h2:mem:trace_listener_source;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        TestDataSourceConnection targetConnection =
                connection("jdbc:h2:mem:trace_listener_target;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        createSource(sourceConnection);
        createTarget(targetConnection);

        RuntimeTraceListener failingListener = event -> {
            throw new IllegalStateException("trace listener failure");
        };
        YakTableSchema schema = sourceSchema();
        JdbcSource source = new JdbcSource(
                new JdbcSourceConfig(
                        sourceConnection,
                        new DataSourceTablePath(null, null, "source_table"),
                        schema,
                        2,
                        2,
                        5),
                DIRECT_CONNECTION,
                failingListener);
        JdbcSink sink = new JdbcSink(
                new JdbcSinkConfig(
                        targetConnection,
                        new DataSourceTablePath(null, null, "target_table"),
                        2,
                        5),
                DIRECT_CONNECTION,
                failingListener);

        assertEquals(
                ExecutionStatus.SUCCEEDED,
                new LocalExecutionEngine().start(source, sink, schema).await(Duration.ofSeconds(5)));

        try (var connection = DIRECT_CONNECTION.open(targetConnection, 5);
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM target_table")) {
            assertTrue(resultSet.next());
            assertEquals(3L, resultSet.getLong(1));
            assertFalse(resultSet.next());
        }
    }

    private YakTableSchema sourceSchema() {
        return JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new DataSourceColumn("name", "VARCHAR", Types.VARCHAR, 100, null, false, 2, false, null),
                new DataSourceColumn("amount", "DECIMAL", Types.DECIMAL, 10, 2, true, 3, false, null)));
    }

    private TestDataSourceConnection connection(String jdbcUrl) {
        return new TestDataSourceConnection("MYSQL", jdbcUrl, DRIVER, "sa", "");
    }

    private void createSource(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute(
                    "CREATE TABLE source_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
            statement.execute("INSERT INTO source_table VALUES (1, 'yak', 10.25)");
            statement.execute("INSERT INTO source_table VALUES (2, 'flow', 20.50)");
            statement.execute("INSERT INTO source_table VALUES (3, 'batch', 30.75)");
        }
    }

    private void createRangeSource(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute(
                    "CREATE TABLE source_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
            for (int id = 1; id <= 10; id++) {
                statement.execute("INSERT INTO source_table VALUES (" + id + ", 'row-" + id + "', " + id + ".00)");
            }
        }
    }

    private void createTarget(TestDataSourceConnection connection) throws Exception {
        try (var opened = DIRECT_CONNECTION.open(connection, 5);
                var statement = opened.createStatement()) {
            statement.execute(
                    "CREATE TABLE target_table (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, amount DECIMAL(10,2))");
        }
    }
}
