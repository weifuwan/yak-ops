package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.core.data.TableId;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the final fallback projection, including its fingerprint, against JDBC. */
class JdbcSplitFallbackTest {
    @Test
    void readsOutOfLongRangeKeyWithoutKeepingAnInvalidCursor() throws Exception {
        verifyFallback(List.of("ID", "PAYLOAD"));
    }

    @Test
    void fallbackDoesNotRetainAHiddenProjectionKey() throws Exception {
        verifyFallback(List.of("PAYLOAD"));
    }

    private void verifyFallback(List<String> columns) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        JdbcConnectionProvider connections = () -> DriverManager.getConnection(url, "sa", "");
        try (var connection = connections.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE LARGE_KEYS(ID DECIMAL(20,0) PRIMARY KEY, PAYLOAD VARCHAR(20))");
            sql.execute("INSERT INTO LARGE_KEYS VALUES (9223372036854775808, 'one')");
        }
        var table = new TableId(null, "PUBLIC", "LARGE_KEYS");
        var dialect = new AnsiJdbcDialect();
        var configuration = new Configuration();
        var splits = new JdbcSplitPlanner(connections, dialect, configuration, Map.of(table, columns))
                .plan(table, 0);
        assertEquals(1, splits.size());
        var split = splits.getFirst();
        assertNull(split.splitColumn());
        assertNull(split.lastEmittedKey());
        assertEquals(columns, split.readColumns());
        try (var reader = new JdbcSourceSplitReader(connections, dialect, configuration)) {
            reader.addSplits(splits);
            var records = reader.fetch();
            assertEquals(split.splitId(), records.nextSplit());
            assertNotNull(records.nextRecordFromSplit());
            assertNull(records.nextRecordFromSplit());
        }
    }
}
