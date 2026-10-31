package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RealtimeSyncIT extends BackendTestBase {
    @Test
    void transformProjectionAndFilterApplySnapshotAndBinlogBoundaryCrossings() throws Exception {
        fixture.source.execute("ALTER TABLE orders ADD COLUMN eligible INT NOT NULL DEFAULT 0");
        fixture.source.execute("UPDATE orders SET eligible=1 WHERE id=1");
        // Flink CDC infers a nullable STRING for UPPER, even from a non-null VARCHAR input.
        fixture.target.execute("CREATE TABLE " + fixture.table("filtered_orders")
                + " (id BIGINT PRIMARY KEY, name TEXT)");
        Map<String, Object> route = new LinkedHashMap<>(fixture.route("orders", "filtered_orders"));
        route.put("source", Map.of("database", fixture.sourceName, "table", "orders"));
        Map<String, Object> parameters = fixture.parameters(
                "SINGLE_TABLE_REALTIME", List.of("FULL", "INCREMENTAL"), List.of(route));
        parameters.put("transform", List.of(Map.of(
                "source-table", fixture.sourceName + ".orders",
                "projection", "id, UPPER(name) AS name",
                "filter", "eligible = 1")));

        String instance = fixture.submit(fixture.create(parameters, true));
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("filtered_orders", List.of(List.of(1L, "ONE")));
        await().alias("Transformed Pipeline enters continuous binlog reading")
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    JsonNode current = fixture.api.call("GET", "/api/v1/task-instances/" + instance);
                    String status = current.path("status").asText();
                    if (Set.of("FINISHED", "FAILED", "CANCELED").contains(status)) {
                        throw new IllegalStateException("Transform Pipeline terminated before streaming: " + status);
                    }
                    String report = current.path("executionReport").asText("");
                    return !report.isBlank()
                            && "STREAMING".equals(ApiClient.JSON.readTree(report).path("phase").asText());
                });

        // Crossing into the filter must turn an UPDATE into a target INSERT.
        fixture.source.execute("UPDATE orders SET eligible=1,name='two-visible' WHERE id=2");
        fixture.awaitTarget("filtered_orders", List.of(List.of(1L, "ONE"), List.of(2L, "TWO-VISIBLE")));
        // Crossing out must retract the projected before-image from the target.
        fixture.source.execute("UPDATE orders SET eligible=0 WHERE id=1");
        fixture.awaitTarget("filtered_orders", List.of(List.of(2L, "TWO-VISIBLE")));
        fixture.source.execute("UPDATE orders SET name='updated' WHERE id=2");
        fixture.awaitTarget("filtered_orders", List.of(List.of(2L, "UPDATED")));
    }

    @Test
    void keylessMysqlXaSnapshotAndBinlogDoNotRequireReceiptTable() throws Exception {
        String instance = fixture.submit(fixture.keylessXaRealtime());
        fixture.awaitState(instance, "RUNNING");
        awaitRows(Map.of("1:10.00", 2L, "2:NULL", 1L));
        fixture.source.execute("UPDATE keyless_source SET amount=11.00 WHERE bucket=2");
        fixture.source.execute(
                "DELETE FROM keyless_source WHERE bucket=1 AND amount=10.00 LIMIT 1");
        fixture.source.execute("INSERT INTO keyless_source VALUES (3,30.00)");
        awaitRows(Map.of("1:10.00", 1L, "2:11.00", 1L, "3:30.00", 1L));
        try (Connection connection = fixture.source.connect();
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA=? AND TABLE_NAME='yak_flow_cdc_receipts'")) {
            statement.setString(1, fixture.sourceName);
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getLong(1), "XA must not require a Receipt table");
            }
        }
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
    }

    private void awaitRows(Map<String, Long> expected) {
        await().alias("MySQL keyless XA rows")
                .atMost(Duration.ofSeconds(150))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertEquals(expected, keylessRows()));
    }

    private Map<String, Long> keylessRows() throws Exception {
        Map<String, Long> values = new LinkedHashMap<>();
        try (Connection connection = fixture.source.connect();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "SELECT bucket,amount,COUNT(*) FROM keyless_target "
                                + "GROUP BY bucket,amount")) {
            while (rows.next()) {
                var amount = rows.getBigDecimal(2);
                values.put(rows.getLong(1) + ":" + (amount == null ? "NULL" : amount.toPlainString()),
                        rows.getLong(3));
            }
        }
        return values;
    }

    @Test
    void snapshotContinuesWithBinlogAndCancellationStopsTheJob() throws Exception {
        String instance = fixture.submit(fixture.realtime());
        fixture.awaitState(instance, "RUNNING");
        fixture.awaitTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        fixture.source.execute("UPDATE orders SET name='changed' WHERE id=1");
        fixture.source.execute("DELETE FROM orders WHERE id=2");
        fixture.source.execute("INSERT INTO orders VALUES(3,'three')");
        fixture.awaitTarget("orders", List.of(List.of(1L, "changed"), List.of(3L, "three")));
        fixture.api.call("POST", "/api/v1/task-instances/" + instance + "/cancel");
        fixture.awaitState(instance, "CANCELED");
        assertEquals(0, fixture.claimCount(instance));
    }
}
