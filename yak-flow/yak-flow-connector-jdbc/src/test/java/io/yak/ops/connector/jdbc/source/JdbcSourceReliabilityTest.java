package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionRetry;
import io.yak.ops.connector.jdbc.database.internal.dialect.AnsiJdbcDialect;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Real H2 reads plus driver-failure injection for checkpoint, cancellation and retry boundaries. */
class JdbcSourceReliabilityTest {

    @Test
    void typeChangeAfterSplitPlanningIsRejectedBeforeEmittingRecords() throws Exception {
        JdbcConnectionOptions db = createTable("jdbc:h2:mem:schema_change;DB_CLOSE_DELAY=-1", true);
        JdbcSourceSplit split = plan(db, "DATASET");
        try (Connection connection = db.openConnection(); Statement sql = connection.createStatement()) {
            sql.execute("ALTER TABLE DATASET ALTER COLUMN LABEL VARCHAR(80)");
        }

        try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(db, new AnsiJdbcDialect(), new Configuration())) {
            reader.addSplits(List.of(split));
            SQLException problem = assertThrows(SQLException.class, reader::fetch);
            assertTrue(problem.getMessage().contains("schema fingerprint"));
        }
    }

    @Test
    void addingAnUnselectedColumnDoesNotChangeTheFrozenReadProjection() throws Exception {
        JdbcConnectionOptions db = createTable("jdbc:h2:mem:schema_extra;DB_CLOSE_DELAY=-1", true);
        JdbcSourceSplit split = plan(db, "DATASET");
        try (Connection connection = db.openConnection(); Statement sql = connection.createStatement()) {
            sql.execute("ALTER TABLE DATASET ADD EXTRA VARCHAR(50)");
        }
        try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(db, new AnsiJdbcDialect(), new Configuration())) {
            reader.addSplits(List.of(split));
            var records = reader.fetch();
            assertEquals(split.splitId(), records.nextSplit());
            assertEquals("first", records.nextRecordFromSplit().record().row().getString(1));
        }
    }

    @Test
    void keylessSplitReplayDoesNotClaimIncrementalCursorRecovery() throws Exception {
        JdbcConnectionOptions db = createTable("jdbc:h2:mem:keyless_replay;DB_CLOSE_DELAY=-1", false);
        JdbcSourceSplit split = plan(db, "DATASET");
        assertEquals(null, split.splitColumn());
        assertEquals(null, split.lastEmittedKey());

        try (JdbcSourceSplitReader first = new JdbcSourceSplitReader(db, new AnsiJdbcDialect(), new Configuration())) {
            first.addSplits(List.of(split));
            assertEquals(split.splitId(), first.fetch().nextSplit());
        }
        try (JdbcSourceSplitReader restored = new JdbcSourceSplitReader(db, new AnsiJdbcDialect(), new Configuration())) {
            restored.addSplits(List.of(split));
            var records = restored.fetch();
            assertEquals(split.splitId(), records.nextSplit());
            String first = records.nextRecordFromSplit().record().row().getString(1);
            String second = records.nextRecordFromSplit().record().row().getString(1);
            assertEquals(java.util.Set.of("first", "second"), java.util.Set.of(first, second));
        }
    }

    @Test
    void onlyTransientConnectionAcquisitionFailuresAreRetried() throws Exception {
        JdbcConnectionOptions db = createTable("jdbc:h2:mem:connect_retry;DB_CLOSE_DELAY=-1", true);
        AtomicInteger opened = new AtomicInteger();
        JdbcConnectionProvider transientProvider = () -> {
            if (opened.incrementAndGet() <= 2) {
                throw new SQLTransientConnectionException("temporary network failure", "08001");
            }
            return db.openConnection();
        };
        try (Connection connection = JdbcConnectionRetry.open(transientProvider, 3)) {
            assertTrue(!connection.isClosed());
        }
        assertEquals(3, opened.get());

        AtomicInteger denied = new AtomicInteger();
        JdbcConnectionProvider wrongAuth = () -> {
            denied.incrementAndGet();
            throw new SQLInvalidAuthorizationSpecException("bad credentials", "28000");
        };
        assertThrows(SQLException.class, () -> JdbcConnectionRetry.open(wrongAuth, 3));
        assertEquals(1, denied.get());

        AtomicInteger exhausted = new AtomicInteger();
        JdbcConnectionProvider unavailable = () -> {
            exhausted.incrementAndGet();
            throw new SQLTransientConnectionException("network unavailable", "08001");
        };
        assertThrows(SQLException.class, () -> JdbcConnectionRetry.open(unavailable, 2));
        assertEquals(2, exhausted.get());
    }

    @Test
    void aPostQueryFetchFailureDoesNotSilentlyReexecuteTheSplit() throws Exception {
        JdbcConnectionOptions db = createTable("jdbc:h2:mem:fetch_failure;DB_CLOSE_DELAY=-1", true);
        JdbcSourceSplit split = plan(db, "DATASET");
        AtomicInteger connections = new AtomicInteger();
        JdbcConnectionProvider failedRead = () -> {
            connections.incrementAndGet();
            return intercept(db.openConnection(), null, null, new AtomicInteger(1));
        };
        try (JdbcSourceSplitReader reader =
                new JdbcSourceSplitReader(failedRead, new AnsiJdbcDialect(), new Configuration())) {
            reader.addSplits(List.of(split));
            SQLException error = assertThrows(SQLException.class, reader::fetch);
            assertTrue(error.getMessage().contains("injected read failure"));
        }
        assertEquals(1, connections.get(), "Read failure must be propagated to Runtime, not retried in-place");
    }

    @Test
    void cancellationIsDistinctFromNormalWakeupAndUnblocksJDBC() throws Exception {
        JdbcConnectionOptions db = createTable("jdbc:h2:mem:cancel_io;DB_CLOSE_DELAY=-1", true);
        JdbcSourceSplit split = plan(db, "DATASET");
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger cancels = new AtomicInteger();
        JdbcConnectionProvider blocked = () -> intercept(db.openConnection(), reading, cancelled, cancels);

        try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(blocked, new AnsiJdbcDialect(), new Configuration())) {
            reader.addSplits(List.of(split));
            CompletableFuture<?> running = CompletableFuture.runAsync(() -> {
                try {
                    reader.fetch();
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
            assertTrue(reading.await(3, TimeUnit.SECONDS));
            reader.wakeUp();
            assertEquals(0, cancels.get(), "Assigning another split must not cancel active SQL");

            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), reader::cancel);
            assertThrows(ExecutionException.class, () -> running.get(3, TimeUnit.SECONDS));
            assertEquals(1, cancels.get());
        }
    }

    private JdbcConnectionOptions createTable(String url, boolean primaryKey) throws Exception {
        JdbcConnectionOptions db = new JdbcConnectionOptions(url, "sa", "");
        try (Connection conn = db.openConnection(); Statement sql = conn.createStatement()) {
            sql.execute("CREATE TABLE DATASET (ID BIGINT " + (primaryKey ? "PRIMARY KEY" : "") + ", LABEL VARCHAR(40))");
            sql.execute("INSERT INTO DATASET VALUES (1, 'first'), (2, 'second')");
        }
        return db;
    }

    private JdbcSourceSplit plan(JdbcConnectionOptions db, String name) throws SQLException {
        return new JdbcSplitPlanner(db, new AnsiJdbcDialect(), new Configuration())
                .plan(new TableId(null, "PUBLIC", name), 0)
                .getFirst();
    }

    /**
     * Wraps one real H2 driver connection to simulate blocking Statement execution or a
     * mid-fetch SQLException without replacing the actual SQL metadata/row implementation.
     */
    private Connection intercept(
            Connection connection, CountDownLatch reading, CountDownLatch cancelled, AtomicInteger trigger) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        PreparedStatement delegate = (PreparedStatement) invoke(connection, method, args);
                        return Proxy.newProxyInstance(
                                PreparedStatement.class.getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (statementProxy, statementMethod, statementArgs) -> {
                                    if (statementMethod.getName().equals("cancel") && cancelled != null) {
                                        trigger.incrementAndGet();
                                        cancelled.countDown();
                                        return null;
                                    }
                                    if (statementMethod.getName().equals("executeQuery")) {
                                        if (reading != null) {
                                            reading.countDown();
                                            if (!cancelled.await(5, TimeUnit.SECONDS)) {
                                                throw new SQLException("Timed out awaiting JDBC cancellation");
                                            }
                                            throw new SQLException("statement cancelled");
                                        }
                                        ResultSet original = (ResultSet) invoke(delegate, statementMethod, statementArgs);
                                        return Proxy.newProxyInstance(
                                                ResultSet.class.getClassLoader(),
                                                new Class<?>[] {ResultSet.class},
                                                (resultProxy, resultMethod, resultArgs) -> {
                                                    if (resultMethod.getName().equals("next")
                                                            && trigger.getAndDecrement() == 0) {
                                                        throw new SQLException("injected read failure", "08006");
                                                    }
                                                    return invoke(original, resultMethod, resultArgs);
                                                });
                                    }
                                    return invoke(delegate, statementMethod, statementArgs);
                                });
                    }
                    return invoke(connection, method, args);
                });
    }

    private Object invoke(Object delegate, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
