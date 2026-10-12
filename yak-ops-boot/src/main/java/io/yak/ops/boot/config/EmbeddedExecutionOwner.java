package io.yak.ops.boot.config;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Holds the single-host embedded execution owner lock for the lifetime of the application.
 *
 * <p>The database owner record blocks another state root or host. Heartbeat expiry
 * never authorizes a second executor to launch a previously submitted Job.
 */
@Component
@DependsOn("yakOpsFlyway")
public class EmbeddedExecutionOwner implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;
    private final String incarnation = UUID.randomUUID().toString();

    public EmbeddedExecutionOwner(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            @Value("${yak.task.execution.state-directory:./data/task-state}") String stateDirectory)
            throws IOException {
        Path root = Path.of(stateDirectory).toAbsolutePath().normalize();
        Files.createDirectories(root);
        root = root.toRealPath();
        channel = FileChannel.open(
                root.resolve(".embedded-owner.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = channel.tryLock();
        if (acquired == null) {
            channel.close();
            throw new IllegalStateException("Another Yak Ops embedded executor owns the local state directory");
        }
        lock = acquired;
        String directory = root.toString();
        String host = InetAddress.getLocalHost().getHostName();
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactions);
            transaction.execute(status -> {
                List<Map<String, Object>> owners = jdbc.queryForList(
                        "SELECT host_id, state_root FROM yak_ops_task_execution_owner WHERE id = ? FOR UPDATE",
                        "embedded");
                LocalDateTime now = LocalDateTime.now();
                if (owners.isEmpty()) {
                    jdbc.update(
                            "INSERT INTO yak_ops_task_execution_owner"
                                    + " (id, host_id, state_root, owner_incarnation, last_seen_time, create_time, update_time)"
                                    + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                            "embedded",
                            host,
                            directory,
                            incarnation,
                            now,
                            now,
                            now);
                } else {
                    Map<String, Object> previous = owners.getFirst();
                    if (!host.equals(previous.get("host_id")) || !directory.equals(previous.get("state_root"))) {
                        throw new IllegalStateException(
                                "Embedded execution owner belongs to another host or state root");
                    }
                    jdbc.update(
                            "UPDATE yak_ops_task_execution_owner SET owner_incarnation = ?, last_seen_time = ?,"
                                    + " update_time = ? WHERE id = ?",
                            incarnation,
                            now,
                            now,
                            "embedded");
                }
                return null;
            });
        } catch (RuntimeException exception) {
            lock.release();
            channel.close();
            throw exception;
        }
    }

    public String incarnation() {
        if (!lock.isValid()) {
            throw new IllegalStateException("Embedded execution lock was lost");
        }
        return incarnation;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        if (lock.isValid()) {
            lock.release();
        }
        channel.close();
    }
}
