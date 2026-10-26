package io.yak.ops.connector.cdc.mysql.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Actual process death across the XA PREPARE, durable Checkpoint, COMMIT and ACK boundaries.
 *
 * <p>All synchronization is test-side, after the real Writer/Committer action. No fake CDC
 * records, mocked database, Runtime fault flag or timing-based crash guess is used.
 */
@Tag("cdc-pipeline")
@Tag("mysql")
@Timeout(400)
class MySqlCdcToMySqlXaCrashRecoveryIT {

    @RegisterExtension
    static final MySqlDatabase SOURCE = MySqlDatabase.cdc();

    @RegisterExtension
    static final MySqlDatabase TARGET = new MySqlDatabase("mysql-target-xa-crash", false, true);

    @Test
    void crashesAfterXaPrepareBeforeCheckpointAndRollsBackOrphanBeforeReplayingBinlog() throws Exception {
        accept(MySqlCdcXaCrashRecoveryProcess.Fault.AFTER_PREPARE);
    }

    @Test
    void crashesAfterDurableCheckpointBeforeXaCommitAndCommitsRecoveredXid() throws Exception {
        accept(MySqlCdcXaCrashRecoveryProcess.Fault.BEFORE_COMMIT);
    }

    @Test
    void crashesAfterXaCommitBeforeAcknowledgmentWithoutDoubleCommit() throws Exception {
        accept(MySqlCdcXaCrashRecoveryProcess.Fault.AFTER_COMMIT);
    }

    private void accept(MySqlCdcXaCrashRecoveryProcess.Fault fault) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sourceName = "xa_cdc_" + suffix;
        String targetName = "xa_dst_" + suffix;
        String instance = "xa_" + suffix;
        String topic = "yakflow_xa_crash_" + suffix;
        Path root = Path.of("target", "test-artifacts", "xa-crash-recovery", suffix).toAbsolutePath();
        Path state = root.resolve("state");
        Path control = root.resolve("control");
        Files.createDirectories(state);
        Files.createDirectories(control);
        int serverId = 59091;
        Process first = null;
        Process second = null;
        try (Connection source = SOURCE.metadata().connect(); Connection target = TARGET.metadata().connect()) {
            execute(source, "CREATE TABLE " + quote(sourceName)
                    + " (bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB");
            execute(target, "CREATE TABLE " + quote(targetName)
                    + " (bucket BIGINT NOT NULL, amount DECIMAL(14,2) NULL) ENGINE=InnoDB");
            execute(source, "INSERT INTO " + quote(sourceName) + " VALUES (1,1.00)");
            Path checkpoint = state.resolve("checkpoint.bin");
            Path firstLog = root.resolve("first.log");
            Path secondLog = root.resolve("restored.log");
            try {
                first = launch(
                        SOURCE.metadata(),
                        TARGET.metadata(),
                        sourceName,
                        targetName,
                        state,
                        control,
                        firstLog,
                        1,
                        false,
                        serverId,
                        topic,
                        instance,
                        fault);
                awaitMarker(first, control.resolve("ready-1"), firstLog);
                awaitRows(first, target, targetName, 1, 1, firstLog);
                assertTrue(Files.isRegularFile(checkpoint), "Hybrid handoff requires a durable Checkpoint");
                assertEquals(0, prepared(target), "Snapshot XA transaction must be fully committed at handoff");
                byte[] initialCheckpoint = Files.readAllBytes(checkpoint);

                Files.writeString(control.resolve("armed"), fault.name());
                execute(source, "INSERT INTO " + quote(sourceName) + " VALUES (2,2.00)");
                awaitMarker(first, control.resolve("delivered-2-1"), firstLog);
                signalCheckpoint(control, "fault-checkpoint");
                String stage = switch (fault) {
                    case AFTER_PREPARE -> "after-prepare";
                    case BEFORE_COMMIT -> "before-commit";
                    case AFTER_COMMIT -> "after-commit";
                    case NONE -> throw new IllegalArgumentException("Expected an XA crash point");
                };
                awaitMarker(first, control.resolve("fault-" + stage), firstLog);

                if (fault == MySqlCdcXaCrashRecoveryProcess.Fault.AFTER_PREPARE) {
                    assertArrayEquals(initialCheckpoint, Files.readAllBytes(checkpoint),
                            "Prepared but uncheckpointed source offset must not be durable");
                    assertEquals(1, prepared(target), "Prepared orphan must be visible in XA RECOVER");
                    assertEquals(0, rows(target, targetName, 2), "Uncommitted XA DML must remain invisible");
                } else {
                    assertFalse(Arrays.equals(initialCheckpoint, Files.readAllBytes(checkpoint)),
                            "Checkpoint must be durable before the Committer is called");
                    assertEquals(
                            fault == MySqlCdcXaCrashRecoveryProcess.Fault.BEFORE_COMMIT ? 1 : 0,
                            prepared(target),
                            "Prepared XID must exist only before the external COMMIT");
                    assertEquals(
                            fault == MySqlCdcXaCrashRecoveryProcess.Fault.BEFORE_COMMIT ? 0 : 1,
                            rows(target, targetName, 2),
                            "Target visibility must follow the actual XA COMMIT");
                }

                kill(first);
                first = null;

                second = launch(
                        SOURCE.metadata(),
                        TARGET.metadata(),
                        sourceName,
                        targetName,
                        state,
                        control,
                        secondLog,
                        2,
                        true,
                        serverId,
                        topic,
                        instance,
                        MySqlCdcXaCrashRecoveryProcess.Fault.NONE);
                awaitMarker(second, control.resolve("ready-2"), secondLog);
                assertEquals(0, prepared(target), "Restore must reconcile every owned Prepared XID");

                if (fault == MySqlCdcXaCrashRecoveryProcess.Fault.AFTER_PREPARE) {
                    // Unpersisted XID was rolled back; the replayed Binlog row is prepared
                    // and committed by a new checkpoint, exactly once.
                    assertEquals(0, rows(target, targetName, 2),
                            "An orphan XA transaction must not have been committed by restore");
                    awaitMarker(second, control.resolve("delivered-2-2"), secondLog);
                    signalCheckpoint(control, "replay-checkpoint");
                    awaitRows(second, target, targetName, 2, 1, secondLog);
                } else {
                    awaitRows(second, target, targetName, 2, 1, secondLog);
                }
                assertEquals(1, rows(target, targetName, 1), "Original Snapshot row must remain unique");
                assertEquals(1, rows(target, targetName, 2), "Recovered mutation must commit exactly once");

                execute(source, "INSERT INTO " + quote(sourceName) + " VALUES (3,3.00)");
                awaitMarker(second, control.resolve("delivered-3-2"), secondLog);
                signalCheckpoint(control, "forward-progress");
                awaitRows(second, target, targetName, 3, 1, secondLog);
                assertEquals(1, rows(target, targetName, 2), "Recovery may not reapply prior mutations");
                assertEquals(0, prepared(target), "No prepared XA transactions may remain after completion");
            } finally {
                killIfAlive(second);
                killIfAlive(first);
                execute(target, "DROP TABLE IF EXISTS " + quote(targetName));
                execute(source, "DROP TABLE IF EXISTS " + quote(sourceName));
            }
        }
    }

    private static Process launch(
            DatabaseMetadata source,
            DatabaseMetadata target,
            String sourceTable,
            String targetTable,
            Path state,
            Path control,
            Path log,
            int generation,
            boolean restore,
            int serverId,
            String topic,
            String instance,
            MySqlCdcXaCrashRecoveryProcess.Fault fault)
            throws Exception {
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.isBlank()) cp = System.getProperty("java.class.path");
        String java = Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                                ? "java.exe"
                                : "java")
                .toString();
        ProcessBuilder process = new ProcessBuilder(
                        java,
                        "-cp",
                        cp,
                        MySqlCdcXaCrashRecoveryProcess.class.getName(),
                        source.host(),
                        Integer.toString(source.port()),
                        sourceTable,
                        target.url(),
                        targetTable,
                        state.toString(),
                        control.toString(),
                        Integer.toString(generation),
                        Boolean.toString(restore),
                        Integer.toString(serverId),
                        topic,
                        instance,
                        fault.name())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        process.environment().put("YAK_XA_SOURCE_USER", source.username());
        process.environment().put("YAK_XA_SOURCE_PASSWORD", source.password());
        process.environment().put("YAK_XA_TARGET_USER", target.username());
        process.environment().put("YAK_XA_TARGET_PASSWORD", target.password());
        return process.start();
    }

    private static void signalCheckpoint(Path control, String name) throws Exception {
        Path tmp = control.resolve(name + ".tmp");
        Files.writeString(tmp, name);
        Files.move(tmp, control.resolve(name + ".request"), StandardCopyOption.ATOMIC_MOVE);
    }

    private static void awaitMarker(Process child, Path marker, Path log) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            assertAlive(child, log);
            if (Files.exists(marker)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + marker.getFileName() + logs(log));
    }

    private static void awaitRows(Process child, Connection db, String table, long bucket, int expected, Path log)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            assertAlive(child, log);
            int current = rows(db, table, bucket);
            if (current == expected) return;
            if (current > expected) throw new AssertionError("Duplicate committed XA rows: " + current + logs(log));
            Thread.sleep(50);
        }
        throw new AssertionError("Missing committed XA bucket " + bucket + logs(log));
    }

    private static void assertAlive(Process child, Path log) throws Exception {
        if (!child.isAlive()) throw new AssertionError("Child JVM died unexpectedly" + logs(log));
    }

    private static int prepared(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("XA RECOVER")) {
            int count = 0;
            while (results.next()) count++;
            return count;
        }
    }

    private static int rows(Connection connection, String table, long bucket) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + quote(table) + " WHERE bucket=" + bucket)) {
            results.next();
            return results.getInt(1);
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void kill(Process child) throws Exception {
        child.destroyForcibly();
        assertTrue(child.waitFor(10, TimeUnit.SECONDS), "Could not kill the child JVM");
        Thread.sleep(250);
    }

    private static void killIfAlive(Process child) throws Exception {
        if (child != null && child.isAlive()) kill(child);
    }

    private static String logs(Path log) {
        try {
            String text = Files.readString(log);
            text = text.replace(SOURCE.metadata().password(), "[REDACTED]")
                    .replace(TARGET.metadata().password(), "[REDACTED]");
            return " (child log tail: " + text.substring(Math.max(0, text.length() - 4500)) + ")";
        } catch (Exception failure) {
            return " (child log unavailable)";
        }
    }

    private static String quote(String table) {
        return "`" + table.replace("`", "``") + "`";
    }
}
