package io.yak.ops.e2e.datasync;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.e2e.support.BackendTestBase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Starts real Task Definitions and checks runtime DDL at the public product boundary. */
class SingleTableRealtimeSchemaIT extends BackendTestBase {
    @Test
    void emptyTenColumnOrdersTableStreamsItsFirstInsertAfterBootstrap() throws Exception {
        fixture.source.execute(
                "CREATE TABLE bootstrap_orders ("
                        + "id BIGINT NOT NULL PRIMARY KEY, order_no VARCHAR(64) NOT NULL,"
                        + "customer_name VARCHAR(100) NOT NULL, amount DECIMAL(14,2) NOT NULL,"
                        + "quantity INT NOT NULL, order_status VARCHAR(24) NOT NULL,"
                        + "order_date DATE, created_at DATETIME(3) NOT NULL,"
                        + "updated_at DATETIME(3), remark VARCHAR(200))");
        Map<String, Object> parameters = new LinkedHashMap<>(fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("SCHEMA", "FULL", "INCREMENTAL"),
                List.of(fixture.route("bootstrap_orders", "bootstrap_orders"))));
        fixture.setSchemaEvolution(parameters, true, false);
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");

        // This is the initially empty source case: only the target DDL may run so far.
        await().alias("Target table exists before the first source INSERT")
                .atMost(Duration.ofSeconds(120))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.prepareStatement(
                                    "SELECT COUNT(*) FROM information_schema.tables "
                                            + "WHERE table_schema=? AND table_name='bootstrap_orders'")) {
                        statement.setString(1, fixture.targetSchema);
                        try (var rows = statement.executeQuery()) {
                            rows.next();
                            assertEquals(1, rows.getLong(1));
                        }
                    }
                });

        // A successful row count cannot prove temporal correctness: a wrongly derived
        // TIMESTAMP(0) accepts inserts but silently discards MySQL DATETIME(3) fractions.
        try (Connection connection = fixture.target.connect();
                var statement = connection.prepareStatement(
                        "SELECT column_name, datetime_precision FROM information_schema.columns "
                                + "WHERE table_schema=? AND table_name='bootstrap_orders' "
                                + "AND column_name IN ('created_at', 'updated_at')")) {
            statement.setString(1, fixture.targetSchema);
            try (var columns = statement.executeQuery()) {
                Map<String, Integer> precisions = new LinkedHashMap<>();
                while (columns.next()) {
                    precisions.put(columns.getString(1), columns.getInt(2));
                }
                assertEquals(Map.of("created_at", 3, "updated_at", 3), precisions);
            }
        }

        fixture.source.execute(
                "INSERT INTO bootstrap_orders VALUES "
                        + "(1,'SO-1001','张三',125.50,2,'CREATED','2026-10-20',"
                        + "'2026-10-20 09:15:23.123',NULL,'第一笔订单'),"
                        + "(2,'SO-1002','李四',880.00,1,'PAID','2026-10-20',"
                        + "'2026-10-20 10:20:00.000',NULL,'第二笔订单'),"
                        + "(3,'SO-1003','王五',49.99,3,'CREATED','2026-10-21',"
                        + "'2026-10-21 11:30:00.000',NULL,NULL)");

        await().alias("First INSERT after CDC schema bootstrap")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT * FROM " + fixture.table("bootstrap_orders") + " ORDER BY id")) {
                        assertTrue(rows.next());
                        assertEquals(1L, rows.getLong("id"));
                        assertEquals("SO-1001", rows.getString("order_no"));
                        assertEquals("张三", rows.getString("customer_name"));
                        assertEquals(new BigDecimal("125.50"), rows.getBigDecimal("amount"));
                        assertEquals(2, rows.getInt("quantity"));
                        assertEquals("CREATED", rows.getString("order_status"));
                        assertEquals(LocalDate.of(2026, 10, 20), rows.getDate("order_date").toLocalDate());
                        assertEquals(LocalDateTime.parse("2026-10-20T09:15:23.123"),
                                rows.getTimestamp("created_at").toLocalDateTime());
                        assertEquals(null, rows.getTimestamp("updated_at"));
                        assertEquals("第一笔订单", rows.getString("remark"));

                        assertTrue(rows.next());
                        assertEquals(2L, rows.getLong("id"));
                        assertEquals(new BigDecimal("880.00"), rows.getBigDecimal("amount"));
                        assertEquals("李四", rows.getString("customer_name"));

                        assertTrue(rows.next());
                        assertEquals(3L, rows.getLong("id"));
                        assertEquals(new BigDecimal("49.99"), rows.getBigDecimal("amount"));
                        assertEquals(null, rows.getString("remark"));
                        assertFalse(rows.next());
                    }
                });
        fixture.source.execute(
                "UPDATE bootstrap_orders SET order_status='PAID', updated_at='2026-10-20 12:34:56.654' WHERE id=1");
        awaitString("bootstrap_orders", "order_status", 1L, "PAID");
        await().alias("Updated MySQL DATETIME(3) retains milliseconds")
                .atMost(Duration.ofSeconds(120))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT updated_at FROM " + fixture.table("bootstrap_orders") + " WHERE id=1")) {
                        assertTrue(rows.next());
                        assertEquals(LocalDateTime.parse("2026-10-20T12:34:56.654"),
                                rows.getTimestamp(1).toLocalDateTime());
                    }
                });
        fixture.awaitState(instance, "RUNNING");
    }

    @Test
    void autoCreatesTargetAndEvolvesColumnsDuringStreaming() throws Exception {
        fixture.source.execute(
                "CREATE TABLE rich_orders (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                        + " amount DECIMAL(12,2) NULL, placed_at DATETIME(3) NULL)");
        fixture.source.execute(
                "INSERT INTO rich_orders VALUES (1,'first',12.34,'2026-10-23 08:00:00.123'),"
                        + "(2,'second',56.78,NULL)");
        Map<String, Object> parameters = new LinkedHashMap<>(
                fixture.parameters(
                        "SINGLE_TABLE_REALTIME",
                        List.of("SCHEMA", "FULL", "INCREMENTAL"),
                        List.of(fixture.route("rich_orders", "rich_orders"))));
        fixture.setSchemaEvolution(parameters, true, false);

        // Target intentionally does not exist. Task publication must not execute the DDL.
        String definition = fixture.create(parameters, true);
        try (Connection connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                                + fixture.targetSchema + "' AND table_name='rich_orders'")) {
            rows.next();
            assertEquals(0, rows.getLong(1));
        }

        String instance = fixture.submit(definition);
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("rich_orders", List.of(List.of(1L, "first"), List.of(2L, "second")));

        fixture.source.execute("ALTER TABLE rich_orders ADD COLUMN tracking_code VARCHAR(64) NULL");
        fixture.source.execute("UPDATE rich_orders SET tracking_code='tracking-1' WHERE id=1");
        awaitString("rich_orders", "tracking_code", 1L, "tracking-1");

        fixture.source.execute("ALTER TABLE rich_orders MODIFY COLUMN name VARCHAR(180) NOT NULL");
        fixture.source.execute("UPDATE rich_orders SET name='a-name-longer-than-thirty-two-characters' WHERE id=1");
        awaitString("rich_orders", "name", 1L, "a-name-longer-than-thirty-two-characters");
        await()
                .alias("PostgreSQL name type widened to VARCHAR(180)")
                .atMost(Duration.ofSeconds(120))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT character_maximum_length FROM information_schema.columns "
                                            + "WHERE table_schema='" + fixture.targetSchema
                                            + "' AND table_name='rich_orders' AND column_name='name'")) {
                        rows.next();
                        assertEquals(180, rows.getInt(1));
                    }
                });
        fixture.awaitState(instance, "RUNNING");
    }

    @Test
    void standaloneDropColumnRequiresExplicitConsentAndUpdatesFollowingRows() throws Exception {
        fixture.source.execute("CREATE TABLE drop_orders (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL, remark VARCHAR(200))");
        fixture.source.execute("INSERT INTO drop_orders VALUES (1,'before','original')");
        Map<String, Object> parameters = new LinkedHashMap<>(fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("SCHEMA", "FULL", "INCREMENTAL"),
                List.of(fixture.route("drop_orders", "drop_orders"))));
        fixture.setSchemaEvolution(parameters, true, false);
        fixture.setSchemaEvolution(parameters, true, true);
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("drop_orders", List.of(List.of(1L, "before")));

        fixture.source.execute("ALTER TABLE drop_orders DROP COLUMN remark");
        awaitColumnAbsent(instance, "drop_orders", "remark");
        fixture.source.execute("UPDATE drop_orders SET name='after-drop' WHERE id=1");
        fixture.awaitTarget("drop_orders", List.of(List.of(1L, "after-drop")));
        fixture.awaitState(instance, "RUNNING");
    }

    @Test
    void standaloneDropWithoutPermissionFailsBeforeTargetDdl() throws Exception {
        fixture.source.execute("CREATE TABLE drop_rejected (id BIGINT PRIMARY KEY, name VARCHAR(64) NOT NULL, remark VARCHAR(200))");
        fixture.source.execute("INSERT INTO drop_rejected VALUES (1,'before','keep')");
        Map<String, Object> parameters = new LinkedHashMap<>(fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("SCHEMA", "FULL", "INCREMENTAL"),
                List.of(fixture.route("drop_rejected", "drop_rejected"))));
        fixture.setSchemaEvolution(parameters, true, false);
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("drop_rejected", List.of(List.of(1L, "before")));

        fixture.source.execute("ALTER TABLE drop_rejected DROP COLUMN remark");
        fixture.awaitState(instance, "FAILED");
        try (Connection connection = fixture.target.connect();
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_schema=? AND table_name=? AND column_name=?")) {
            statement.setString(1, fixture.targetSchema);
            statement.setString(2, "drop_rejected");
            statement.setString(3, "remark");
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test
    void legacyRealtimeTaskDoesNotSilentlyApplySchemaChanges() throws Exception {
        // The field is intentionally absent. Existing persisted Task Definitions stay disabled.
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_REALTIME",
                List.of("FULL", "INCREMENTAL"),
                List.of(fixture.route("orders", "orders")));
        fixture.createTarget("orders");
        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));

        fixture.source.execute("ALTER TABLE orders ADD COLUMN tracking_code VARCHAR(64) NULL");
        fixture.source.execute("UPDATE orders SET tracking_code='not-applied' WHERE id=1");
        fixture.awaitState(instance, "FAILED");
        try (Connection connection = fixture.target.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='"
                                + fixture.targetSchema
                                + "' AND table_name='orders' AND column_name='tracking_code'")) {
            rows.next();
            assertEquals(0, rows.getLong(1));
        }
    }

    private void awaitColumnAbsent(String instance, String table, String column) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode state = fixture.api.call("GET", "/api/v1/task-instances/" + instance);
            String status = state.path("status").asText();
            if (status.equals("FAILED")
                    || status.equals("CANCELED")
                    || status.equals("FINISHED")) {
                // Reuse the fixture's failure artifact capture so the assertion exposes the
                // task's original error instead of hiding it behind the full DDL timeout.
                fixture.awaitState(instance, "RUNNING");
            }
            if (status.equals("RUNNING")) {
                try (Connection connection = fixture.target.connect();
                        var query = connection.prepareStatement(
                                "SELECT COUNT(*) FROM information_schema.columns "
                                        + "WHERE table_schema=? AND table_name=? AND column_name=?")) {
                    query.setString(1, fixture.targetSchema);
                    query.setString(2, table);
                    query.setString(3, column);
                    try (var rows = query.executeQuery()) {
                        rows.next();
                        if (rows.getInt(1) == 0) {
                            return;
                        }
                    }
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("CDC pure DDL was not applied within 120 seconds: " + table + "." + column);
    }

    private void awaitString(String table, String column, long id, String expected) {
        // Source DDL is applied asynchronously. Wait for the target column before
        // querying it: PostgreSQL SQLSTATE 42703 is not an Awaitility assertion failure.
        await()
                .alias("Runtime target column " + table + "." + column)
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.prepareStatement(
                                    "SELECT COUNT(*) FROM information_schema.columns "
                                            + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
                        statement.setString(1, fixture.targetSchema);
                        statement.setString(2, table);
                        statement.setString(3, column);
                        try (var rows = statement.executeQuery()) {
                            rows.next();
                            assertEquals(1, rows.getLong(1));
                        }
                    }
                });
        await()
                .alias("Runtime DDL and CDC data " + table + "." + column)
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    try (Connection connection = fixture.target.connect();
                            var statement = connection.createStatement();
                            var rows = statement.executeQuery(
                                    "SELECT " + column + " FROM " + fixture.table(table) + " WHERE id=" + id)) {
                        rows.next();
                        assertEquals(expected, rows.getString(1));
                    }
                });
    }
}
