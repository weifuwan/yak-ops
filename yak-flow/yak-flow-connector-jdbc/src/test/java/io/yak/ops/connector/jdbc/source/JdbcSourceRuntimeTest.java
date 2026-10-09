package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.source.InputStatus;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Reads real JDBC ResultSets through the YakFlow Runtime and restores a checkpointed split
 * through the production Connector Base fetcher without mock ResultSets.
 */
class JdbcSourceRuntimeTest {

    @Test
    void threeTablesShareOneSourceAndRunThroughParallelRuntime() throws Exception {
        JdbcConnectionOptions connection = new JdbcConnectionOptions("jdbc:h2:mem:jdbc_flow_multi;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection opened = connection.openConnection();
                Statement sql = opened.createStatement()) {
            sql.execute("CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, PAYLOAD VARCHAR(32))");
            sql.execute("CREATE TABLE CUSTOMERS (ID INT PRIMARY KEY, PAYLOAD VARCHAR(32))");
            sql.execute("CREATE TABLE NOTES (VALUE VARCHAR(32))");
            for (int index = 1; index <= 21; index++) {
                sql.execute("INSERT INTO ORDERS VALUES (" + index + ", 'o" + index + "')");
            }
            sql.execute("INSERT INTO CUSTOMERS VALUES (1,'a'),(2,'b')");
            sql.execute("INSERT INTO NOTES VALUES ('first'),('second')");
        }

        TableId orders = new TableId(null, "PUBLIC", "ORDERS");
        TableId customers = new TableId(null, "PUBLIC", "CUSTOMERS");
        TableId notes = new TableId(null, "PUBLIC", "NOTES");
        Configuration options = new Configuration();
        options.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 4);
        options.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 8);
        options.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 2);
        JdbcSource source = new JdbcSource(connection, List.of(orders, customers, notes), options);

        List<TableRecord> results = new CopyOnWriteArrayList<>();
        Sink<TableRecord> sink = context -> new SinkWriter<>() {
            @Override
            public void write(TableRecord row, Context ignored) {
                results.add(row);
            }

            @Override
            public void flush(boolean endOfInput) {}

            @Override
            public void close() {}
        };
        SourceTransformation<TableRecord> sourceNode =
                new SourceTransformation<>("jdbc-multi", source, TableRecord.class, 3);
        SinkTransformation<TableRecord> sinkNode = new SinkTransformation<>(sourceNode, "collect", sink, 1);
        StreamGraph graph = new StreamGraphGenerator(sinkNode, new Configuration()).generate();
        JobClient job = new EmbeddedPipelineExecutor().execute(graph, new Configuration()).get(5, TimeUnit.SECONDS);
        job.getJobExecutionResult().get(20, TimeUnit.SECONDS);

        assertEquals(JobStatus.FINISHED, job.getJobStatus().get(5, TimeUnit.SECONDS));
        assertEquals(25, results.size());
        Map<TableId, Long> byTable =
                results.stream().collect(Collectors.groupingBy(TableRecord::tableId, Collectors.counting()));
        assertEquals(21L, byTable.get(orders));
        assertEquals(2L, byTable.get(customers));
        assertEquals(2L, byTable.get(notes));
        assertEquals(
                21,
                results.stream()
                        .filter(record -> orders.equals(record.tableId()))
                        .map(row -> ((Number) row.row().getField(0)).intValue())
                        .distinct()
                        .count());
    }

    @Test
    void readerSnapshotResumesFromLastSuccessfullyEmittedPrimaryKey() throws Exception {
        JdbcConnectionOptions connection = new JdbcConnectionOptions("jdbc:h2:mem:jdbc_flow_resume;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection opened = connection.openConnection();
                Statement sql = opened.createStatement()) {
            sql.execute("CREATE TABLE ITEMS (ID BIGINT PRIMARY KEY, NAME VARCHAR(32))");
            for (int id = 1; id <= 10; id++) {
                sql.execute("INSERT INTO ITEMS VALUES (" + id + ",'item" + id + "')");
            }
        }

        TableId table = new TableId(null, "PUBLIC", "ITEMS");
        JdbcSourceSplit initial = new JdbcSourceSplit(
                "table-0-split-0", table, List.of("ID", "NAME"), "ID", 1L, 10L, null);
        JdbcSourceSplit checkpointed;
        try (JdbcSourceReader reader = new JdbcSourceReader(
                connection, new AnsiJdbcDialect(), new Configuration(), new DemoContext())) {
            reader.addSplits(List.of(initial));
            reader.start();
            reader.notifyNoMoreSplits();
            List<TableRecord> first = new ArrayList<>();
            while (first.isEmpty()) {
                InputStatus status = reader.pollNext(first::add);
                if (status == InputStatus.NOTHING_AVAILABLE) {
                    reader.isAvailable().get(3, TimeUnit.SECONDS);
                }
            }
            assertEquals(1L, ((Number) first.getFirst().row().getField(0)).longValue());
            checkpointed = reader.snapshotState(5).getFirst();
            assertEquals(1L, checkpointed.lastEmittedKey());
        }

        List<TableRecord> remaining = new ArrayList<>();
        try (JdbcSourceReader reader = new JdbcSourceReader(
                connection, new AnsiJdbcDialect(), new Configuration(), new DemoContext())) {
            reader.addSplits(List.of(checkpointed));
            reader.start();
            reader.notifyNoMoreSplits();
            for (int attempt = 0; attempt < 500; attempt++) {
                InputStatus status = reader.pollNext(remaining::add);
                if (status == InputStatus.END_OF_INPUT) {
                    break;
                }
                if (status == InputStatus.NOTHING_AVAILABLE) {
                    reader.isAvailable().get(3, TimeUnit.SECONDS);
                }
            }
            assertTrue(reader.snapshotState(6).isEmpty());
        }
        assertEquals(9, remaining.size());
        assertEquals(2L, ((Number) remaining.getFirst().row().getField(0)).longValue());
        assertEquals(10L, ((Number) remaining.getLast().row().getField(0)).longValue());
    }

    @Test
    void failedEmissionDoesNotAdvanceCheckpointPosition() throws Exception {
        JdbcConnectionOptions connection = new JdbcConnectionOptions("jdbc:h2:mem:jdbc_flow_fail;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection opened = connection.openConnection();
                Statement sql = opened.createStatement()) {
            sql.execute("CREATE TABLE DEMO (ID BIGINT PRIMARY KEY)");
            sql.execute("INSERT INTO DEMO VALUES (1),(2)");
        }
        JdbcSourceSplit initial = new JdbcSourceSplit(
                "table-0-split-0",
                new TableId(null, "PUBLIC", "DEMO"),
                List.of("ID"),
                "ID",
                1L,
                2L,
                null);
        try (JdbcSourceReader reader = new JdbcSourceReader(
                connection, new AnsiJdbcDialect(), new Configuration(), new DemoContext())) {
            reader.addSplits(List.of(initial));
            reader.start();
            reader.notifyNoMoreSplits();
            while (true) {
                try {
                    InputStatus status = reader.pollNext(record -> {
                        throw new IOException("downstream rejected record");
                    });
                    if (status == InputStatus.NOTHING_AVAILABLE) {
                        reader.isAvailable().get(3, TimeUnit.SECONDS);
                    }
                } catch (IOException expected) {
                    break;
                }
            }
            assertEquals(initial, reader.snapshotState(9).getFirst());
        }
    }

    private static final class DemoContext implements SourceReaderContext {

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
    }
}
