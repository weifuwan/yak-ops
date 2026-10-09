package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.factory.JdbcCatalogFactory;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        String sqlTypes = "YF_SRC_IT_C";
        String qualifiedC = dialect.quoteIdentifier(sqlTypes);
        String integerType = oracle ? "NUMBER(19)" : "BIGINT";
        String stringType = oracle ? "VARCHAR2(48)" : "VARCHAR(48)";
        String decimalType = oracle ? "NUMBER(18,2)" : "DECIMAL(18,2)";
        String binaryType = oracle ? "BLOB" : mysql ? "BLOB" : "BYTEA";
        String timestampType = oracle ? "TIMESTAMP(6)" : mysql ? "DATETIME(6)" : "TIMESTAMP(6)";

        try (Connection connection = connectionOptions.openConnection();
                Statement ddl = connection.createStatement()) {
            try {
                ddl.execute("CREATE TABLE " + qualifiedA
                        + " (" + dialect.quoteIdentifier("ID") + " " + integerType + " PRIMARY KEY, "
                        + dialect.quoteIdentifier("LABEL") + " " + stringType + ")");
                ddl.execute("CREATE TABLE " + qualifiedB
                        + " (" + dialect.quoteIdentifier("MESSAGE") + " " + stringType + ")");
                ddl.execute("CREATE TABLE " + qualifiedC + " ("
                        + dialect.quoteIdentifier("ID") + " " + integerType + " PRIMARY KEY, "
                        + dialect.quoteIdentifier("AMOUNT") + " " + decimalType + ", "
                        + dialect.quoteIdentifier("LABEL") + " " + stringType + ", "
                        + dialect.quoteIdentifier("PAYLOAD") + " " + binaryType + ", "
                        + dialect.quoteIdentifier("EVENT_TS") + " " + timestampType + ", "
                        + dialect.quoteIdentifier("EVENT_DATE") + " DATE, "
                        + dialect.quoteIdentifier("OPTIONAL_VALUE") + " " + stringType + ")");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO " + qualifiedC + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setLong(1, 101L);
                    insert.setBigDecimal(2, new BigDecimal("123.45"));
                    insert.setString(3, "typed record");
                    insert.setBytes(4, new byte[] {1, 2, 3, 4});
                    insert.setTimestamp(5, java.sql.Timestamp.valueOf("2026-10-09 12:34:56.123456"));
                    insert.setDate(6, java.sql.Date.valueOf("2026-10-09"));
                    insert.setNull(7, java.sql.Types.VARCHAR);
                    insert.executeUpdate();
                }
                for (int id = 1; id <= 23; id++) {
                    ddl.execute("INSERT INTO " + qualifiedA + " VALUES (" + id + ", 'item" + id + "')");
                }
                ddl.execute("INSERT INTO " + qualifiedB + " VALUES ('one')");
                ddl.execute("INSERT INTO " + qualifiedB + " VALUES ('two')");

                TableId first = new TableId(
                        mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), sqlId);
                TableId second = new TableId(
                        mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), sqlNotes);
                TableId third = new TableId(
                        mysql ? connection.getCatalog() : null, mysql ? null : connection.getSchema(), sqlTypes);
                Configuration options = new Configuration();
                options.set(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT, 5);
                options.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 6);
                options.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 2);
                options.set(JdbcSourceOptions.RESULT_SET_FETCH_SIZE, 3);

                // Catalog is now owned and discovered by the Connector's SPI, not Datasource.
                try (JdbcCatalog catalog = JdbcCatalogFactory.create(connectionOptions)) {
                    assertTrue(!catalog.listDatabases().isEmpty());
                    List<TableId> catalogTables = catalog.listTables(
                            mysql ? connection.getCatalog() : null,
                            mysql ? null : connection.getSchema());
                    assertTrue(catalogTables.stream().anyMatch(table -> first.table().equals(table.table())));
                    assertTrue(catalogTables.stream().anyMatch(table -> third.table().equals(table.table())));
                    assertEquals(List.of("ID"), catalog.getTable(first).primaryKeys());
                    assertEquals("DECIMAL(18, 2)", catalog.getTable(third)
                            .columns()
                            .get(1)
                            .dataType()
                            .asSerializableString());
                    assertTrue(!catalog.tableExists(new TableId(first.catalog(), first.schema(), "YF_NOT_A_TABLE")));
                }

                List<JdbcSourceSplit> partitions =
                        new JdbcSplitPlanner(connectionOptions, dialect, options).plan(first, 0);
                assertTrue(partitions.size() > 1, "Integral primary keys must produce multiple splits");

                JdbcSource source = new JdbcSource(connectionOptions, List.of(first, second, third), options);
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
                assertEquals(26, received.size());
                assertEquals(23L, counts.get(first));
                assertEquals(2L, counts.get(second));
                assertEquals(1L, counts.get(third));
                TableRecord typedRecord = received.stream()
                        .filter(row -> row.tableId().equals(third))
                        .findFirst()
                        .orElseThrow();
                assertEquals(new BigDecimal("123.45"), typedRecord.row().getDecimal(1, 18, 2));
                assertEquals("typed record", typedRecord.row().getString(2));
                org.junit.jupiter.api.Assertions.assertArrayEquals(
                        new byte[] {1, 2, 3, 4}, typedRecord.row().getBinary(3));
                assertEquals(
                        LocalDateTime.parse("2026-10-09T12:34:56.123456"),
                        typedRecord.row().getTimestamp(4, 6));
                if (oracle) {
                    assertEquals(
                            LocalDate.of(2026, 10, 9).atStartOfDay(),
                            typedRecord.row().getTimestamp(5, 0));
                } else {
                    assertEquals(LocalDate.of(2026, 10, 9), typedRecord.row().getDate(5));
                }
                assertTrue(typedRecord.row().isNullAt(6));
                assertTrue(received.stream().allMatch(row -> row.rowKind() == RowKind.INSERT));
                assertEquals(23L, received.stream()
                        .filter(row -> row.tableId().equals(first))
                        .map(row -> ((Number) row.row().getField(0)).longValue())
                        .distinct()
                        .count());

                // Reuse the originally planned split after changing the selected column type.
                // The running Reader must reject it before emitting data, in every real driver.
                String label = dialect.quoteIdentifier("LABEL");
                String alteration = oracle
                        ? "ALTER TABLE " + qualifiedA + " MODIFY (" + label + " VARCHAR2(72))"
                        : mysql
                                ? "ALTER TABLE " + qualifiedA + " MODIFY COLUMN " + label + " VARCHAR(72)"
                                : "ALTER TABLE " + qualifiedA + " ALTER COLUMN " + label + " TYPE VARCHAR(72)";
                ddl.execute(alteration);
                try (JdbcSourceSplitReader stale = new JdbcSourceSplitReader(connectionOptions, dialect, options)) {
                    stale.addSplits(List.of(partitions.getFirst()));
                    SQLException failure = assertThrows(SQLException.class, stale::fetch);
                    assertTrue(failure.getMessage().contains("schema fingerprint"));
                }
            } finally {
                try {
                    ddl.execute("DROP TABLE " + qualifiedC);
                } finally {
                    try {
                        ddl.execute("DROP TABLE " + qualifiedB);
                    } finally {
                        ddl.execute("DROP TABLE " + qualifiedA);
                    }
                }
            }
        }
    }


    @Test
    void mysqlCursorFetchReadsLargeTableInSmallBatches() throws Exception {
        String url = requiredProperty("jdbc.it.url");
        assumeTrue(url.startsWith("jdbc:mysql:"), "MySQL cursor fetch acceptance only");
        if (!Boolean.getBoolean("jdbc.it.allow-write")) {
            throw new IllegalStateException("Set -Djdbc.it.allow-write=true for disposable test databases only");
        }
        String user = requiredProperty("jdbc.it.user");
        String password = System.getProperty("jdbc.it.password", "");
        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions(url, user, password);
        JdbcConnectionOptions cursorOptions =
                new JdbcConnectionOptions(url, user, password, null, Map.of("useCursorFetch", "true"));
        JdbcDialect dialect = JdbcDialects.forUrl(url);
        String name = "YF_SRC_CURSOR_IT";
        String tableSql = dialect.quoteIdentifier(name);
        TableId table;
        try (Connection setup = connectionOptions.openConnection(); Statement ddl = setup.createStatement()) {
            ddl.execute("DROP TABLE IF EXISTS " + tableSql);
            ddl.execute("CREATE TABLE " + tableSql + " (ID BIGINT PRIMARY KEY, LABEL VARCHAR(40))");
            table = new TableId(setup.getCatalog(), null, name);
            try (PreparedStatement insert = setup.prepareStatement("INSERT INTO " + tableSql + " VALUES (?, ?)")) {
                for (int index = 1; index <= 8192; index++) {
                    insert.setLong(1, index);
                    insert.setString(2, "row" + index);
                    insert.addBatch();
                    if (index % 256 == 0) {
                        insert.executeBatch();
                    }
                }
            }
        }
        try {
            Configuration config = new Configuration();
            config.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 1);
            config.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 64);
            config.set(JdbcSourceOptions.RESULT_SET_FETCH_SIZE, 32);
            JdbcSourceSplit split = new JdbcSplitPlanner(cursorOptions, dialect, config)
                    .plan(table, 0)
                    .getFirst();
            int count = 0;
            boolean finished = false;
            try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(cursorOptions, dialect, config)) {
                reader.addSplits(List.of(split));
                for (int attempt = 0; attempt < 200 && !finished; attempt++) {
                    var batch = reader.fetch();
                    int size = 0;
                    if (split.splitId().equals(batch.nextSplit())) {
                        while (batch.nextRecordFromSplit() != null) {
                            size++;
                        }
                    }
                    assertTrue(size <= 64);
                    count += size;
                    finished = batch.finishedSplits().contains(split.splitId());
                }
            }
            assertTrue(finished);
            assertEquals(8192, count);
        } finally {
            try (Connection cleanup = connectionOptions.openConnection(); Statement ddl = cleanup.createStatement()) {
                ddl.execute("DROP TABLE IF EXISTS " + tableSql);
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
