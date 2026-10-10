package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.base.sink.writer.BatchFlushPolicy;
import io.yak.ops.connector.jdbc.sink.writer.JdbcWriter;
import io.yak.ops.connector.jdbc.database.dialect.AbstractDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Multi-table Changelog behavior and checkpoint-flush/replay boundaries against real H2 JDBC. */
class JdbcMultiTableSinkTest {

    private static final TableId SOURCE_A = new TableId(null, null, "SOURCE_A");
    private static final TableId SOURCE_B = new TableId(null, null, "SOURCE_B");
    private static final TableId TARGET_A = new TableId(null, null, "TARGET_A");
    private static final TableId TARGET_B = new TableId(null, null, "TARGET_B");
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
    void preservesMutationOrderAcrossTablesAndPrimaryKeyChanges() throws Exception {
        String url = databaseUrl();
        createTables(url);
        JdbcSink sink = sink(url, h2Dialect(), upsertPlans(), 100);
        try (JdbcWriter writer = sink.createWriter(context())) {
            writer.write(a(RowKind.INSERT, "old", 1L), RECORD_CONTEXT);
            writer.write(b(RowKind.INSERT, 1L, new byte[] {1, 2}), RECORD_CONTEXT);
            writer.write(a(RowKind.UPDATE_BEFORE, "old", 1L), RECORD_CONTEXT);
            writer.write(a(RowKind.UPDATE_AFTER, "new", 11L), RECORD_CONTEXT);
            writer.write(b(RowKind.UPDATE_BEFORE, 1L, null), RECORD_CONTEXT);
            byte[] changed = new byte[] {7, 8};
            writer.write(b(RowKind.UPDATE_AFTER, 1L, changed), RECORD_CONTEXT);
            changed[0] = 99;
            writer.write(a(RowKind.INSERT, "second", 2L), RECORD_CONTEXT);
            writer.flush(false);
        }
        assertEquals(0L, rowCount(url, "TARGET_A", 1L));
        assertEquals(1L, rowCount(url, "TARGET_A", 11L));
        assertEquals(1L, rowCount(url, "TARGET_A", 2L));
        try (Connection connection = open(url);
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT PAYLOAD FROM TARGET_B WHERE ID = 1")) {
            assertTrue(result.next());
            assertArrayEquals(new byte[] {7, 8}, result.getBytes(1));
        }

        try (JdbcWriter writer = sink.createWriter(context())) {
            writer.write(b(RowKind.DELETE, 1L, null), RECORD_CONTEXT);
            writer.flush(false);
        }
        assertEquals(0L, rowCount(url, "TARGET_B", 1L));
    }

    @Test
    void rollsBackAllTablesOnBatchFailureWithoutPartialCommitOrRetry() throws Exception {
        String url = databaseUrl();
        createTables(url);
        List<JdbcTableWritePlan> plans = List.of(
                new JdbcTableWritePlan(SOURCE_A, TARGET_A, targetSchemaA(), JdbcWriteMode.APPEND, sourceSchemaA(),
                        Map.of("MESSAGE", "TEXT")),
                new JdbcTableWritePlan(SOURCE_B, TARGET_B, schemaB(), JdbcWriteMode.APPEND));
        try (JdbcWriter writer = sink(url, new AnsiJdbcDialect(), plans, 100).createWriter(context())) {
            writer.write(a(RowKind.INSERT, "first", 1L), RECORD_CONTEXT);
            writer.write(b(RowKind.INSERT, 4L, new byte[] {4}), RECORD_CONTEXT);
            writer.write(a(RowKind.INSERT, "duplicate", 1L), RECORD_CONTEXT);
            assertThrows(SQLException.class, () -> writer.flush(false));
            assertThrows(IllegalStateException.class, () -> writer.write(a(RowKind.INSERT, "late", 5L), RECORD_CONTEXT));
        }
        assertEquals(0L, rowCount(url, "TARGET_A", 1L));
        assertEquals(0L, rowCount(url, "TARGET_B", 4L));
    }

    @Test
    void closeDiscardsUnflushedEventsAndUpsertReplayIsIdempotent() throws Exception {
        String url = databaseUrl();
        createTables(url);
        JdbcSink sink = sink(url, h2Dialect(), upsertPlans(), 100);
        try (JdbcWriter first = sink.createWriter(context())) {
            first.write(a(RowKind.INSERT, "confirmed", 1L), RECORD_CONTEXT);
            first.flush(false);
            first.write(a(RowKind.INSERT, "uncommitted", 2L), RECORD_CONTEXT);
        }
        assertEquals(1L, rowCount(url, "TARGET_A", 1L));
        assertEquals(0L, rowCount(url, "TARGET_A", 2L));

        // A completed checkpoint can replay already committed rows at least once.
        // Native UPSERT is idempotent for identical values, unlike APPEND without a key.
        try (JdbcWriter recovered = sink.createWriter(context())) {
            recovered.write(a(RowKind.INSERT, "confirmed", 1L), RECORD_CONTEXT);
            recovered.write(a(RowKind.INSERT, "uncommitted", 2L), RECORD_CONTEXT);
            recovered.flush(false);
        }
        assertEquals(1L, rowCount(url, "TARGET_A", 1L));
        assertEquals(1L, rowCount(url, "TARGET_A", 2L));
    }

    @Test
    void sizeFlushCountsAllTablesWithinTheSameTransaction() throws Exception {
        String url = databaseUrl();
        createTables(url);
        try (JdbcWriter writer = sink(url, new AnsiJdbcDialect(), appendPlans(), 2).createWriter(context())) {
            writer.write(a(RowKind.INSERT, "first", 1L), RECORD_CONTEXT);
            assertEquals(0L, rowCount(url, "TARGET_A", 1L));
            writer.write(b(RowKind.INSERT, 4L, new byte[] {4}), RECORD_CONTEXT);
            assertEquals(1L, rowCount(url, "TARGET_A", 1L));
            assertEquals(1L, rowCount(url, "TARGET_B", 4L));
            writer.write(a(RowKind.INSERT, "discarded", 2L), RECORD_CONTEXT);
        }
        assertEquals(0L, rowCount(url, "TARGET_A", 2L));
    }

    @Test
    void rejectsUnknownRoutesInvalidMappingsNullKeysAndAppendChangelog() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(
                        SOURCE_A, TARGET_A, targetSchemaA(), JdbcWriteMode.APPEND, sourceSchemaA(),
                        Map.of("UNKNOWN", "TEXT")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcTableWritePlan(
                        SOURCE_A, TARGET_A, targetSchemaA(), JdbcWriteMode.APPEND, sourceSchemaA(),
                        Map.of("ID", "TEXT")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSink(
                        () -> open(databaseUrl()),
                        h2Dialect(),
                        List.of(upsertPlans().getFirst(), upsertPlans().getFirst()),
                        new BatchFlushPolicy(10, Duration.ZERO)));

        String url = databaseUrl();
        createTables(url);
        try (JdbcWriter writer = sink(url, h2Dialect(), upsertPlans(), 100).createWriter(context())) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> writer.write(
                            new TableRecord(new TableId(null, null, "UNREGISTERED"), RowKind.INSERT,
                                    GenericRowData.of("x", 1L)),
                            RECORD_CONTEXT));
        }
        try (JdbcWriter writer = sink(url, h2Dialect(), upsertPlans(), 100).createWriter(context())) {
            assertThrows(
                    NullPointerException.class,
                    () -> writer.write(a(RowKind.DELETE, null, null), RECORD_CONTEXT));
        }
        try (JdbcWriter writer = sink(url, new AnsiJdbcDialect(), appendPlans(), 100).createWriter(context())) {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> writer.write(a(RowKind.UPDATE_AFTER, "changed", 1L), RECORD_CONTEXT));
        }
        assertEquals(0L, rowCount(url, "TARGET_A", 1L));
    }

    @Test
    void sameKeyUpdatePreservesCascadeDependentsAtBatchSizeOne() throws Exception {
        String url = databaseUrl();
        createTables(url);
        try (Connection connection = open(url); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO TARGET_A VALUES (1, 'old')");
            statement.execute("CREATE TABLE CHILD_ROWS (ID BIGINT PRIMARY KEY, PARENT_ID BIGINT NOT NULL,"
                    + " FOREIGN KEY (PARENT_ID) REFERENCES TARGET_A(ID) ON DELETE CASCADE)");
            statement.execute("INSERT INTO CHILD_ROWS VALUES (100, 1)");
        }

        try (JdbcWriter writer = sink(url, h2Dialect(), upsertPlans(), 1).createWriter(context())) {
            writer.write(a(RowKind.UPDATE_BEFORE, "old", 1L), RECORD_CONTEXT);
            assertEquals(1L, rowCount(url, "TARGET_A", 1L));
            assertEquals(1L, rowCount(url, "CHILD_ROWS", 100L));
            writer.write(a(RowKind.UPDATE_AFTER, "new", 1L), RECORD_CONTEXT);
        }

        assertEquals(1L, rowCount(url, "CHILD_ROWS", 100L));
        try (Connection connection = open(url);
                Statement statement = connection.createStatement();
                var result = statement.executeQuery("SELECT MESSAGE FROM TARGET_A WHERE ID = 1")) {
            assertTrue(result.next());
            assertEquals("new", result.getString(1));
        }
    }

    @Test
    void primaryKeyMoveIsCommittedOnlyAfterItsAfterImageAtBatchSizeOne() throws Exception {
        String url = databaseUrl();
        createTables(url);
        try (Connection connection = open(url); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO TARGET_A VALUES (1, 'old')");
        }
        try (JdbcWriter writer = sink(url, h2Dialect(), upsertPlans(), 1).createWriter(context())) {
            writer.write(a(RowKind.UPDATE_BEFORE, "old", 1L), RECORD_CONTEXT);
            assertEquals(1L, rowCount(url, "TARGET_A", 1L));
            writer.write(a(RowKind.UPDATE_AFTER, "new", 20L), RECORD_CONTEXT);
        }
        assertEquals(0L, rowCount(url, "TARGET_A", 1L));
        assertEquals(1L, rowCount(url, "TARGET_A", 20L));
    }

    @Test
    void checkpointRejectsIncompleteUpdateAndDoesNotCommitEarlierRows() throws Exception {
        String url = databaseUrl();
        createTables(url);
        try (Connection connection = open(url); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO TARGET_A VALUES (1, 'old')");
        }
        try (JdbcWriter writer = sink(url, h2Dialect(), upsertPlans(), 10).createWriter(context())) {
            writer.write(a(RowKind.INSERT, "pending", 9L), RECORD_CONTEXT);
            writer.write(a(RowKind.UPDATE_BEFORE, "old", 1L), RECORD_CONTEXT);
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> writer.flush(false));
            assertTrue(error.getMessage().contains("incomplete JDBC UPDATE"));
            assertThrows(IllegalStateException.class, () -> writer.write(a(RowKind.UPDATE_AFTER, "new", 1L), RECORD_CONTEXT));
        }
        assertEquals(0L, rowCount(url, "TARGET_A", 9L));
        assertEquals(1L, rowCount(url, "TARGET_A", 1L));
    }

    @Test
    void rejectsInterleavedBeforeAfterWithoutPartialCommit() throws Exception {
        String url = databaseUrl();
        createTables(url);
        try (JdbcWriter writer = sink(url, h2Dialect(), upsertPlans(), 10).createWriter(context())) {
            writer.write(a(RowKind.UPDATE_BEFORE, "old", 1L), RECORD_CONTEXT);
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> writer.write(b(RowKind.INSERT, 8L, new byte[] {8}), RECORD_CONTEXT));
            assertTrue(error.getMessage().contains("UPDATE_BEFORE must be followed"));
            assertThrows(IllegalStateException.class, () -> writer.flush(false));
        }
        assertEquals(0L, rowCount(url, "TARGET_B", 8L));
    }

    private static List<JdbcTableWritePlan> upsertPlans() {
        return List.of(
                new JdbcTableWritePlan(SOURCE_A, TARGET_A, targetSchemaA(), JdbcWriteMode.UPSERT, sourceSchemaA(),
                        Map.of("MESSAGE", "TEXT")),
                new JdbcTableWritePlan(SOURCE_B, TARGET_B, schemaB(), JdbcWriteMode.UPSERT));
    }

    private static List<JdbcTableWritePlan> appendPlans() {
        return List.of(
                new JdbcTableWritePlan(SOURCE_A, TARGET_A, targetSchemaA(), JdbcWriteMode.APPEND, sourceSchemaA(),
                        Map.of("MESSAGE", "TEXT")),
                new JdbcTableWritePlan(SOURCE_B, TARGET_B, schemaB(), JdbcWriteMode.APPEND));
    }

    private static TableSchema sourceSchemaA() {
        return new TableSchema(
                List.of(
                        new Column("TEXT", LogicalTypes.varchar(50)),
                        new Column("ID", LogicalTypes.BIGINT.copy(false))),
                List.of("ID"));
    }

    private static TableSchema targetSchemaA() {
        return new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("MESSAGE", LogicalTypes.varchar(50))),
                List.of("ID"));
    }

    private static TableSchema schemaB() {
        return new TableSchema(
                List.of(
                        new Column("ID", LogicalTypes.BIGINT.copy(false)),
                        new Column("PAYLOAD", LogicalTypes.varbinary(20))),
                List.of("ID"));
    }

    private static TableRecord a(RowKind kind, String value, Long key) {
        return new TableRecord(SOURCE_A, kind, GenericRowData.of(value, key));
    }

    private static TableRecord b(RowKind kind, long key, byte[] payload) {
        return new TableRecord(SOURCE_B, kind, GenericRowData.of(key, payload));
    }

    private static JdbcSink sink(String url, JdbcDialect dialect, List<JdbcTableWritePlan> plans, int batchSize) {
        return JdbcSink.builder()
                .withConnectionProvider(() -> open(url))
                .withDialect(dialect)
                .withTablePlans(plans)
                .withBatchFlushPolicy(new BatchFlushPolicy(batchSize, Duration.ZERO))
                .build();
    }

    private static JdbcDialect h2Dialect() {
        return new AbstractDialect() {
            @Override
            public String quoteIdentifier(String name) {
                return "\"" + JdbcDialect.requireIdentifier(name).replace("\"", "\"\"") + "\"";
            }

            @Override
            public String qualifiedTable(TableId table) {
                return quoteIdentifier(table.table());
            }

            @Override
            public String upsertSql(TableId table, TableSchema schema) {
                String columns = schema.columns().stream()
                        .map(column -> quoteIdentifier(column.name()))
                        .collect(java.util.stream.Collectors.joining(", "));
                String keys = schema.primaryKeys().stream()
                        .map(this::quoteIdentifier)
                        .collect(java.util.stream.Collectors.joining(", "));
                String placeholders = schema.columns().stream()
                        .map(column -> "?")
                        .collect(java.util.stream.Collectors.joining(", "));
                return "MERGE INTO " + qualifiedTable(table) + " (" + columns
                        + ") KEY (" + keys + ") VALUES (" + placeholders + ")";
            }
        };
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:yak_jdbc_multisink_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
    }

    private static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static void createTables(String url) throws SQLException {
        try (Connection connection = open(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE TARGET_A (ID BIGINT PRIMARY KEY, MESSAGE VARCHAR(50))");
            statement.execute("CREATE TABLE TARGET_B (ID BIGINT PRIMARY KEY, PAYLOAD VARBINARY(20))");
        }
    }

    private static long rowCount(String url, String table, long key) throws SQLException {
        try (Connection connection = open(url);
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE ID = " + key)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static WriterInitContext context() {
        return new WriterInitContext() {
            @Override
            public TaskInfo getTaskInfo() {
                throw new UnsupportedOperationException("TaskInfo is not needed by these JDBC tests");
            }

            @Override
            public Configuration getConfiguration() {
                return new Configuration();
            }
        };
    }
}
