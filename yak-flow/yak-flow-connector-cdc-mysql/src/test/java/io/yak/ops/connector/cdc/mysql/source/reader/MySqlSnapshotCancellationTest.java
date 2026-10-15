package io.yak.ops.connector.cdc.mysql.source.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlSnapshotSplit;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Coordinates a blocked driver query with latches instead of sleep-based cancellation tests. */
class MySqlSnapshotCancellationTest {
    private static final TableId TABLE = new TableId("shop", null, "orders");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
    private static final MySqlSnapshotSplit SPLIT = new MySqlSnapshotSplit("snapshot", "frozen", TABLE, null, null, null);

    @Test
    void cancelsRunningJdbcQueryWithoutBlockingTheCaller() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger timeout = new AtomicInteger();
        var statement = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setQueryTimeout" -> timeout.set((Integer) args[0]);
                        case "executeQuery" -> {
                            executing.countDown();
                            if (!cancelled.await(5, TimeUnit.SECONDS)) throw new SQLException("Driver cancellation missing");
                            throw new SQLException("Driver query cancelled");
                        }
                        case "cancel" -> cancelled.countDown();
                        default -> { }
                    }
                    return null;
                });
        var connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement") ? statement : null);
        try (var reader = new MySqlSnapshotSplitReader(() -> connection, new AnsiJdbcDialect(), Map.of(TABLE, SCHEMA));
                var executor = Executors.newSingleThreadExecutor()) {
            var opening = executor.submit(() -> { reader.open(SPLIT); return null; });
            try {
                assertTrue(executing.await(5, TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(1), reader::cancel);
                assertTrue(cancelled.await(5, TimeUnit.SECONDS));
                var failure = assertThrows(ExecutionException.class, () -> opening.get(5, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof SQLException);
                assertEquals(60, timeout.get());
                assertThrows(SQLException.class, () -> reader.open(SPLIT));
            } finally {
                cancelled.countDown();
            }
        }
    }

    @Test
    void cancellationBeforeOpenDoesNotAcquireAConnection() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        try (var reader = new MySqlSnapshotSplitReader(() -> {
            connections.incrementAndGet();
            throw new SQLException("Unexpected connection");
        }, new AnsiJdbcDialect(), Map.of(TABLE, SCHEMA))) {
            reader.cancel();
            assertThrows(SQLException.class, () -> reader.open(SPLIT));
            assertEquals(0, connections.get());
        }
    }
}
