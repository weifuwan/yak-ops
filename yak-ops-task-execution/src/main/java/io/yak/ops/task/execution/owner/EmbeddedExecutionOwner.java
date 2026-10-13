package io.yak.ops.task.execution.owner;

import io.yak.ops.dao.repository.task.ExecutionOwnerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Holds the single-host embedded execution owner lock for the lifetime of the application.
 *
 * <p>The database owner record blocks another state root or host. Heartbeat expiry
 * never authorizes a second executor to launch a previously submitted Job.
 */
@Component
@DependsOn("yakOpsFlyway")
public class EmbeddedExecutionOwner implements AutoCloseable {

    @Resource
    private ExecutionOwnerRepository owners;

    @Value("${yak.task.execution.state-directory:./data/task-state}")
    private String stateDirectory;

    private FileChannel channel;
    private FileLock lock;
    private final String incarnation = UUID.randomUUID().toString();

    @PostConstruct
    public void acquire() throws IOException {
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
        try {
            owners.register(InetAddress.getLocalHost().getHostName(), root.toString(), incarnation);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
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
        if (lock != null && lock.isValid()) {
            lock.release();
        }
        if (channel != null) {
            channel.close();
        }
    }
}
