package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import io.yak.ops.flow.runtime.graph.StreamGraph;
import io.yak.ops.flow.runtime.graph.StreamGraphGenerator;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Real-database acceptance for MySQL, PostgreSQL, and Oracle JDBC Source.
 *
 * <p>This is an opt-in, write-capable integration test against a disposable database. It is
 * intentionally named *IT to keep it out of ordinary Maven verify. Missing explicit connection
 * settings fail the test rather than silently reporting a skipped acceptance run.
 */
class JdbcSourceDatabaseIT {

    @Test
    void sourceReadsMultipleTablesThroughTheActualYakFlowRuntime() throws Exception {
        String url = requiredProperty("jdbc.it.url");
        String user = requiredProperty("jdbc.it.user");
        String password = System.getProperty("jdbc.it.password", "");
        if (!Boolean.getBoolean("jdbc.it.allow-write")) {
            throw new IllegalStateException("Set -Djdbc.it.allow-write=true for disposable test databases only");
        }

        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions(url, user, password);
        JdbcDialect dialect = JdbcDialects.forUrl(url);
        boolean oracle = url.startsWith("jdbc:oracle:");
        boolean mysql = url.startsWith("jdbc:mysql:");
        String sqlId = "YF_SRC_IT_A";
        String sqlNotes = "YF_SRC_IT_B";
        String qualifiedA = dialect.quoteIdentifier(sqlId);
        String qualifiedB = dialect.quoteIdentifier(sqlNotes);
        String integerType = oracle ? "NUMBER(19)" : "BIGINT";
        String stringType = oracle ? "VARCHAR2(48)" : "VARCHAR(48)";

        try (Connection connection = connectionOptions.openConnection();
                Statement ddl = connection.createStatement()) {
            try {
                ddl.execute("CREATE TABLE " + qualifiedA
                        + " (" + dialect.quoteIdentifier("ID") + " " + integerType + " PRIMARY KEY, "
                        + dialect.quoteIdentifier("LABEL") + " " + stringType + ")");
                ddl.execute("CREATE TABLE " + qualifiedB
                        + " (" + dialect.quoteIdentifier("MESSAGE") + " " + stringType + ")");
                for (int id = 1; id <= 23; id++) {
                    ddl.execute("INSERT INTO " + qualifiedA + " VALUES (" + id + ", 'item" + id + "')");
                }
                ddl.execute("INSERT INTO " + qualifiedB + " VALUES ('one')");
                ddl.execute("INSERT INTO " + qualifiedB + " VALUES ('two')");

                TableId first = new TableId(
                        mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), sqlId);
                TableId second = new TableId(
                        mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), sqlNotes);
                Configuration options = new Configuration();
                options.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 5);
                options.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 6);
                options.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 2);
                options.set(JdbcSourceOptions.RESULT_SET_FETCH_SIZE, 3);

                List<?> partitions = new JdbcSplitPlanner(connectionOptions, dialect, options).plan(first, 0);
                assertTrue(partitions.size() > 1, "Integral primary keys must produce multiple splits");

                JdbcSource source = new JdbcSource(connectionOptions, List.of(first, second), options);
                CopyOnWriteArrayList<TableRecord> received = new CopyOnWriteArrayList<>();
                Sink<TableRecord> collector = context -> new SinkWriter<>() {
                    @Override
                    public void write(TableRecord element, Context metadata) {
                        received.add(element);
                    }

                    @Override
                    public void flush(boolean endOfInput) {}

                    @Override
                    public void close() {}
                };

                SourceTransformation<TableRecord> sourceNode =
                        new SourceTransformation<>("jdbc-real-source", source, TableRecord.class, 3);
                SinkTransformation<TableRecord> sinkNode =
                        new SinkTransformation<>(sourceNode, "collect", collector, 1);
                StreamGraph graph = new StreamGraphGenerator(sinkNode, new Configuration()).generate();
                JobClient job = new EmbeddedPipelineExecutor()
                        .execute(graph, new Configuration())
                        .get(10, TimeUnit.SECONDS);
                job.getJobExecutionResult().get(60, TimeUnit.SECONDS);
                assertEquals(JobStatus.FINISHED, job.getJobStatus().get(10, TimeUnit.SECONDS));

                Map<TableId, Long> counts = received.stream()
                        .collect(Collectors.groupingBy(TableRecord::tableId, Collectors.counting()));
                assertEquals(25, received.size());
                assertEquals(23L, counts.get(first));
                assertEquals(2L, counts.get(second));
                assertTrue(received.stream().allMatch(row -> row.rowKind() == RowKind.INSERT));
                assertEquals(23L, received.stream()
                        .filter(row -> row.tableId().equals(first))
                        .map(row -> ((Number) row.row().getField(0)).longValue())
                        .distinct()
                        .count());
            } finally {
                try {
                    ddl.execute("DROP TABLE " + qualifiedB);
                } finally {
                    ddl.execute("DROP TABLE " + qualifiedA);
                }
            }
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required database acceptance setting: " + name);
        }
        return value;
    }
}
