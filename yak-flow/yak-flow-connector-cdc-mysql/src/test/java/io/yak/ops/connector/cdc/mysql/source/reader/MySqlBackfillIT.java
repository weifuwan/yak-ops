package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.*;

import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceConfig;
import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetOrder;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Real JDBC and Debezium with writes placed after scanning and before the per-chunk High. */
@Tag("cdc")
@Timeout(180)
class MySqlBackfillIT {
    @RegisterExtension static final MySqlDatabase DATABASE = MySqlDatabase.cdc();
    private static final TableSchema SCHEMA = new TableSchema(List.of(
            new Column("id", LogicalTypes.BIGINT.copy(false)),
            new Column("name", LogicalTypes.varchar(100).copy(false))), List.of("id"));

    @Test
    void backfillsRepeatedUpdatesDeletesCrossChunkMovesAndIgnoresOtherTables() throws Exception {
        String table = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        String other = table + "_other";
        execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "CREATE TABLE " + other + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)",
                "INSERT INTO " + table + " VALUES(1,'old'),(2,'deleted'),(50,'moving')");
        TableId id = new TableId("yak_cdc_it", null, table);
        TableId another = new TableId("yak_cdc_it", null, other);
        try {
            var config = config(Map.of(id, SCHEMA, another, SCHEMA), 6501, table);
            try (var reader = new MySqlSnapshotBackfillReader(config, 0, () -> {
                execute("UPDATE " + table + " SET name='intermediate' WHERE id=1",
                        "UPDATE " + table + " SET name='final' WHERE id=1",
                        "DELETE FROM " + table + " WHERE id=2",
                        "UPDATE " + table + " SET id=150 WHERE id=50",
                        "INSERT INTO " + table + " VALUES(5,'new')",
                        "INSERT INTO " + other + " VALUES(7,'not-this-chunk')");
                return null;
            })) {
                var split = reader.read(new MySqlSnapshotSplit("chunk", "fp", id, null, 100L, null));
                Map<Long,String> values = new LinkedHashMap<>();
                for (var row : split.output().rows()) {
                    assertEquals(RowKind.INSERT, row.rowKind());
                    values.put(row.row().getLong(0), row.row().getString(1));
                }
                assertEquals(Map.of(1L,"final",5L,"new"), values);
                assertTrue(BinlogOffsetOrder.compare(split.output().high(), split.output().low()) > 0);
                assertEquals(0, split.outputPosition());
            }
        } finally { execute("DROP TABLE " + table, "DROP TABLE " + other); }
    }

    @Test
    void quietEmptyChunkAndFollowingScanFinishFromActualWatermarks() throws Exception {
        String table = "quiet_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        TableId id = new TableId("yak_cdc_it", null, table);
        try (var reader = new MySqlSnapshotBackfillReader(config(Map.of(id, SCHEMA), 6601, table), 0)) {
            var empty = reader.read(new MySqlSnapshotSplit("empty", "fp", id, null, 10L, null));
            assertTrue(empty.output().rows().isEmpty());
            assertEquals(0, BinlogOffsetOrder.compare(empty.output().low(), empty.output().high()));
            execute("INSERT INTO " + table + " VALUES(20,'present')");
            var second = reader.read(new MySqlSnapshotSplit("second", "fp", id, 10L, null, null));
            assertEquals(1, second.output().rows().size());
            assertEquals(20L, second.output().rows().getFirst().row().getLong(0));
        } finally { execute("DROP TABLE " + table); }
    }

    private static MySqlCdcSourceConfig config(Map<TableId, TableSchema> schemas, int serverId, String topic) {
        var db = DATABASE.metadata();
        return new MySqlCdcSourceConfig(db.host(), db.port(), db.username(), db.password(), serverId, topic, schemas, 256);
    }
    private static void execute(String... statements) throws Exception {
        var db = DATABASE.metadata();
        try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password());
                var sql = connection.createStatement()) {
            for (String statement : statements) sql.execute(statement);
        }
    }
}
