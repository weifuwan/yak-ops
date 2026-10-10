package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.OracleJdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect;
import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.api.connector.sink.SinkWriter;
import io.yak.ops.core.api.connector.sink.WriterInitContext;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JdbcSinkTest {

    private static final TableId SOURCE = new TableId(null, null, "SOURCE_DOCS");
    private static final TableId TARGET = new TableId(null, null, "TARGET_DOCS");
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
    void flushesOnBatchSizeAndAtCheckpointWithoutCloseTimeFlush() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, TITLE VARCHAR(80))");
        JdbcSink sink = sink(url, basicSchema(), 2);

        try (JdbcWriter writer = sink.createWriter(context())) {
            writer.write(record(1L, "first"), RECORD_CONTEXT);
            assertEquals(0L, rowCount(url));
            writer.write(record(2L, "second"), RECORD_CONTEXT);
            assertEquals(2L, rowCount(url));
            writer.write(record(3L, "third"), RECORD_CONTEXT);
            assertEquals(2L, rowCount(url));
            writer.flush(false);
            assertEquals(3L, rowCount(url));
            writer.write(record(4L, "fourth"), RECORD_CONTEXT);
        }
        assertEquals(3L, rowCount(url));
    }

    @Test
    void normalEndOfInputFlushIsExplicit() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, TITLE VARCHAR(80))");
        try (JdbcWriter writer = sink(url, basicSchema(), 10).createWriter(context())) {
            writer.write(record(1L, "finish"), RECORD_CONTEXT);
            writer.flush(true);
            assertEquals(1L, rowCount(url));
        }
        assertEquals(1L, rowCount(url));
    }

    @Test
    void driverBatchFailureRollsBackAndNeverRetriesAmbiguousWrites() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, TITLE VARCHAR(80))");
        try (JdbcWriter writer = sink(url, basicSchema(), 2).createWriter(context())) {
            writer.write(record(1L, "before"), RECORD_CONTEXT);
            assertThrows(SQLException.class, () -> writer.write(record(1L, "duplicate"), RECORD_CONTEXT));
            assertEquals(0L, rowCount(url));
            assertThrows(IllegalStateException.class, () -> writer.write(record(2L, "after"), RECORD_CONTEXT));
            assertThrows(IllegalStateException.class, () -> writer.flush(false));
        }
        assertEquals(0L, rowCount(url));
    }

    @Test
    void snapshotsMutableBinaryFieldsBeforeTheFlush() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, PAYLOAD VARBINARY(20))");
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("PAYLOAD", LogicalTypes.varbinary(20))),
                List.of("ID"));
        byte[] payload = new byte[] {1, 2, 3};
        GenericRowData row = GenericRowData.of(8L, payload);
        try (JdbcWriter writer = sink(url, schema, 10).createWriter(context())) {
            writer.write(new TableRecord(SOURCE, RowKind.INSERT, row), RECORD_CONTEXT);
            payload[0] = 99;
            row.setField(1, new byte[] {4, 5, 6});
            writer.flush(false);
        }
        try (Connection connection = open(url);
                Statement sql = connection.createStatement();
                var results = sql.executeQuery("SELECT PAYLOAD FROM TARGET_DOCS WHERE ID = 8")) {
            assertTrue(results.next());
            assertArrayEquals(new byte[] {1, 2, 3}, results.getBytes(1));
        }
    }

    @Test
    void rejectsOtherTablesAndChangelogEventsBeforeFlushing() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, TITLE VARCHAR(80))");
        try (JdbcWriter writer = sink(url, basicSchema(), 10).createWriter(context())) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> writer.write(
                            new TableRecord(TARGET, RowKind.INSERT, GenericRowData.of(1L, "wrong")),
                            RECORD_CONTEXT));
        }
        try (JdbcWriter writer = sink(url, basicSchema(), 10).createWriter(context())) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> writer.write(
                            new TableRecord(SOURCE, RowKind.DELETE, GenericRowData.of(1L, "delete")),
                            RECORD_CONTEXT));
        }
        assertEquals(0L, rowCount(url));
    }

    @Test
    void cancelRejectsFurtherWritesWithoutFlushing() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, TITLE VARCHAR(80))");
        try (JdbcWriter writer = sink(url, basicSchema(), 10).createWriter(context())) {
            writer.write(record(1L, "pending"), RECORD_CONTEXT);
            writer.cancel();
            assertThrows(java.util.concurrent.CancellationException.class, () -> writer.flush(false));
        }
        assertEquals(0L, rowCount(url));
    }

    @Test
    void closesConnectionWhenWriterInitContextCannotProvideTimers() throws Exception {
        String url = databaseUrl();
        createTable(url, "CREATE TABLE TARGET_DOCS (ID BIGINT PRIMARY KEY, TITLE VARCHAR(80))");
        AtomicReference<Connection> opened = new AtomicReference<>();
        JdbcSink sink = new JdbcSink(
                () -> {
                    Connection connection = open(url);
                    opened.set(connection);
                    return connection;
                },
                new AnsiJdbcDialect(),
                new JdbcTableWritePlan(SOURCE, TARGET, basicSchema(), JdbcWriteMode.APPEND),
                new BatchFlushPolicy(2, Duration.ofSeconds(1)));
        assertThrows(UnsupportedOperationException.class, () -> sink.createWriter(context()));
        assertTrue(opened.get().isClosed());
    }

    @Test
    void nativeUpsertUsesExistingDialectSqlWithoutEmulatedSelectUpdate() {
        JdbcTableWritePlan plan = new JdbcTableWritePlan(SOURCE, TARGET, basicSchema(), JdbcWriteMode.UPSERT);
        assertTrue(plan.sql(new MySqlJdbcDialect()).contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(plan.sql(new PostgresJdbcDialect()).contains("ON CONFLICT"));
        assertTrue(plan.sql(new OracleJdbcDialect()).startsWith("MERGE INTO"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(
                        SOURCE,
                        TARGET,
                        new TableSchema(basicSchema().columns(), List.of()),
                        JdbcWriteMode.UPSERT));
    }

    private static JdbcSink sink(String url, TableSchema schema, int batchSize) {
        return JdbcSink.builder()
                .withConnectionProvider(() -> open(url))
                .withDialect(new AnsiJdbcDialect())
                .withSourceTable(SOURCE)
                .withTargetTable(TARGET)
                .withSchema(schema)
                .withBatchFlushPolicy(new BatchFlushPolicy(batchSize, Duration.ZERO))
                .build();
    }

    private static TableSchema basicSchema() {
        return new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("TITLE", LogicalTypes.varchar(80))),
                List.of("ID"));
    }

    private static TableRecord record(long id, String title) {
        return new TableRecord(SOURCE, RowKind.INSERT, GenericRowData.of(id, title));
    }

    private static WriterInitContext context() {
        return new WriterInitContext() {
            @Override
            public TaskInfo getTaskInfo() {
                throw new UnsupportedOperationException("TaskInfo not needed by JDBC unit tests");
            }

            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }
        };
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:yak_jdbc_sink_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
    }

    private static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static void createTable(String url, String ddl) throws SQLException {
        try (Connection connection = open(url); Statement sql = connection.createStatement()) {
            sql.execute(ddl);
        }
    }

    private static long rowCount(String url) throws SQLException {
        try (Connection connection = open(url);
                Statement sql = connection.createStatement();
                var count = sql.executeQuery("SELECT COUNT(*) FROM TARGET_DOCS")) {
            count.next();
            return count.getLong(1);
        }
    }
}
