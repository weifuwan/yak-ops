package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableMetadata;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TimeType;
import io.yak.ops.core.types.TimestampType;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("mysql")
class MySqlSourceIT extends JdbcSourceTestBase {
    @RegisterExtension
    static final MySqlDatabase DATABASE = new MySqlDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }

    @Override
    protected Map<String, String> sqlTypes() {
        return Map.of(
                "integer",
                "BIGINT",
                "string",
                "VARCHAR(48)",
                "decimal",
                "DECIMAL(18,2)",
                "binary",
                "BLOB",
                "timestamp",
                "DATETIME(6)");
    }

    @Override
    protected String widenColumn(String table, String column) {
        return "ALTER TABLE " + table + " MODIFY COLUMN " + column + " VARCHAR(72)";
    }

    @Test
    void mysqlWhereAndUniqueSplitKeyRetainThePredicateAcrossCursorRestore() throws Exception {
        JdbcConnectionOptions settings = new JdbcConnectionOptions(database().url(), database().username(), database().password());
        var provider = new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(settings);
        JdbcDialect dialect = JdbcDialects.forUrl(settings.url());
        try (Connection connection = provider.getConnection(); Statement statement = connection.createStatement()) {
            TableId table = new TableId(connection.getCatalog(), null, TestNames.unique("filtered_source"));
            String tableSql = dialect.qualifiedTable(table);
            statement.execute("CREATE TABLE " + tableSql
                    + " (ID BIGINT PRIMARY KEY, SEQUENCE_ID BIGINT NOT NULL UNIQUE, LABEL VARCHAR(32))");
            try {
                statement.execute("INSERT INTO " + tableSql
                        + " VALUES (1,10,'one'),(2,20,'excluded'),(3,30,'three'),(4,40,'four'),(5,50,'five')");
                Configuration options = new Configuration();
                options.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 1);
                options.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 2);
                JdbcReadOptions read = new JdbcReadOptions("ID = 1 OR ID >= 3", "SEQUENCE_ID", 2, 5);
                JdbcSourceSplit split = new JdbcSplitPlanner(provider, dialect, options,
                        Map.of(table, List.of("LABEL")), Map.of(), Map.of(table, read)).plan(table, 0).getFirst();
                assertEquals("SEQUENCE_ID", split.splitColumn());
                assertEquals(List.of("LABEL", "SEQUENCE_ID", "ID"), split.readColumns());
                JdbcSourceSplit resumed;
                try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(provider, dialect, options)) {
                    reader.addSplits(List.of(split));
                    var batch = reader.fetch();
                    assertEquals(split.splitId(), batch.nextSplit());
                    var first = batch.nextRecordFromSplit();
                    var second = batch.nextRecordFromSplit();
                    assertEquals("one", first.record().row().getString(0));
                    assertEquals("three", second.record().row().getString(0));
                    assertEquals(1, second.record().row().getArity());
                    resumed = split.withLastEmittedKey(30L);
                }
                var serializer = new io.yak.ops.connector.jdbc.source.split.JdbcSourceSplitSerializer();
                resumed = serializer.deserialize(serializer.getVersion(), serializer.serialize(resumed));
                List<String> remaining = new java.util.ArrayList<>();
                try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(provider, dialect, options)) {
                    reader.addSplits(List.of(resumed));
                    boolean finished = false;
                    for (int attempt = 0; attempt < 5 && !finished; attempt++) {
                        var batch = reader.fetch();
                        if (resumed.splitId().equals(batch.nextSplit())) {
                            io.yak.ops.connector.jdbc.source.reader.JdbcRecordAndPosition record;
                            while ((record = batch.nextRecordFromSplit()) != null) remaining.add(record.record().row().getString(0));
                        }
                        finished = batch.finishedSplits().contains(resumed.splitId());
                    }
                    assertTrue(finished);
                }
                assertEquals(List.of("four", "five"), remaining);
            } finally {
                statement.execute("DROP TABLE " + tableSql);
            }
        }
    }

    @Test
    void mysqlCatalogPreservesFractionalSecondsForTargetSchema() throws Exception {
        JdbcConnectionOptions options =
                new JdbcConnectionOptions(database().url(), database().username(), database().password());
        JdbcDialect dialect = JdbcDialects.forUrl(database().url());
        String name = TestNames.unique("temporal").toUpperCase(java.util.Locale.ROOT);
        String quoted = dialect.quoteIdentifier(name);
        try (Connection connection = new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(
                        options)
                .getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + quoted
                    + " (ID BIGINT PRIMARY KEY, CREATED_AT DATETIME(3), UPDATED_AT DATETIME(6),"
                    + " WHOLE_SECOND DATETIME, EVENT_TIME TIME(2))");
            try {
                var schema = JdbcTableMetadata.readTable(
                        connection, dialect, new TableId(connection.getCatalog(), null, name));
                assertEquals(3, ((TimestampType) schema.column(1).dataType()).precision());
                assertEquals(6, ((TimestampType) schema.column(2).dataType()).precision());
                assertEquals(0, ((TimestampType) schema.column(3).dataType()).precision());
                assertEquals(2, ((TimeType) schema.column(4).dataType()).precision());
            } finally {
                statement.execute("DROP TABLE " + quoted);
            }
        }
    }

    @Test
    void mysqlCursorFetchReadsLargeTableInSmallBatches() throws Exception {
        String url = database().url();
        String user = database().username();
        String password = database().password();
        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions(url, user, password);
        JdbcConnectionOptions cursorOptions =
                new JdbcConnectionOptions(url, user, password, null, Map.of("useCursorFetch", "true"));
        JdbcDialect dialect = JdbcDialects.forUrl(url);
        String name = TestNames.unique("cursor").toUpperCase(java.util.Locale.ROOT);
        String tableSql = dialect.quoteIdentifier(name);
        TableId table;
        try (Connection setup = new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(
                                connectionOptions)
                        .getConnection();
                Statement ddl = setup.createStatement()) {
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
            try (Connection cleanup = new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(
                                    connectionOptions)
                            .getConnection();
                    Statement ddl = cleanup.createStatement()) {
                ddl.execute("DROP TABLE IF EXISTS " + tableSql);
            }
        }
    }
}
