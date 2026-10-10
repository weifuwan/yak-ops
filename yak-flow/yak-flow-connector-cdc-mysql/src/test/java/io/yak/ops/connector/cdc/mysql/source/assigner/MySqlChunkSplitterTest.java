package io.yak.ops.connector.cdc.mysql.source.assigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.reader.MySqlSnapshotSplitReader;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises sparse chunk keys and restart from consumed-key progress on a real JDBC query. */
class MySqlChunkSplitterTest {

    @Test
    void plansSparseHalfOpenRangesAndRestartsAtExclusiveCursor() throws Exception {
        String url = "jdbc:h2:mem:hybrid_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var c = DriverManager.getConnection(url);
                var statement = c.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            statement.execute("INSERT INTO orders VALUES (1,'a'),(100,'b'),(1000,'c'),(5000,'d'),(6000,'e')");
        }
        TableId table = new TableId(null, null, "orders");
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(100))),
                List.of("id"));
        var dialect = JdbcDialects.forUrl("jdbc:mysql:");
        var splitter = new MySqlChunkSplitter(() -> DriverManager.getConnection(url), dialect, Map.of(table, schema), 2);
        var splits = splitter.plan(table, "fingerprint", 0);
        assertEquals(3, splits.size());
        assertEquals(null, splits.getFirst().lowerInclusive());
        assertEquals(1000L, splits.getFirst().upperExclusive());
        assertEquals(1000L, splits.get(1).lowerInclusive());
        assertEquals(6000L, splits.get(1).upperExclusive());
        assertEquals(null, splits.getLast().upperExclusive());

        var reader = new MySqlSnapshotSplitReader(
                () -> DriverManager.getConnection(url), dialect, Map.of(table, schema));
        reader.open(splits.getFirst());
        var first = reader.fetch();
        assertEquals("mysql-snapshot-0-0", first.nextSplit());
        var one = first.nextRecordFromSplit();
        assertEquals(1L, one.snapshotKey());
        // A Checkpoint of the first delivered key resumes at the second row, never replaying 1.
        reader.close();
        reader.open(splits.getFirst().withLastEmittedKey(one.snapshotKey()));
        var remaining = reader.fetch();
        assertEquals("mysql-snapshot-0-0", remaining.nextSplit());
        assertEquals(100L, remaining.nextRecordFromSplit().snapshotKey());
        assertEquals(null, remaining.nextRecordFromSplit());
        assertTrue(remaining.finishedSplits().contains("mysql-snapshot-0-0"));
        reader.close();
    }

    @Test
    void rejectsUnsupportedCompositeOrNonNumericChunkKeys() {
        var wrong = new TableSchema(
                List.of(new Column("id", LogicalTypes.varchar(10).copy(false))), List.of("id"));
        assertThrows(IllegalArgumentException.class, () -> MySqlChunkSplitter.splitKey(wrong));
    }
}
