package io.yak.ops.e2e.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.awaitility.Awaitility.await;

import io.yak.ops.e2e.support.BackendTestBase;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealtimeSyncIT extends BackendTestBase {
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
