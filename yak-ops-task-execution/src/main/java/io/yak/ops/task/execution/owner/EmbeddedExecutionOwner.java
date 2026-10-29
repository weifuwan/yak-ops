package io.yak.ops.task.execution.owner;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.UUID;
import org.apache.dolphinscheduler.registry.api.Registry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Owns the single embedded dispatcher through DolphinScheduler JDBC Registry and
 * a local filesystem lock. No second dispatcher starts after a failed lease check.
 */
@Component
@DependsOn("yakOpsFlyway")
public class EmbeddedExecutionOwner implements AutoCloseable {
    public static final String REGISTRY_PATH = "/yak-ops/masters/embedded";
    public static final String REGISTRY_LOCK = "/yak-ops/locks/embedded";

    @Resource
    private Registry registry;

    @Value("${yak.task.execution.state-directory:./data/task-state}")
    private String stateDirectory;

    @Value("${registry.session-timeout:PT60S}")
    private Duration registrySessionTimeout;

    private FileChannel channel;
    private FileLock lock;
    private boolean registered;
    private final String incarnation = UUID.randomUUID().toString();

    @PostConstruct
    public void acquire() throws IOException {
        Path root = Path.of(stateDirectory).toAbsolutePath().normalize();
        Files.createDirectories(root);
        root = root.toRealPath();
        channel = FileChannel.open(
                root.resolve(".embedded-owner.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            throw new IllegalStateException("Another embedded executor owns the local state directory");
        }
        try {
            if (!registry.isConnected()) {
                throw new IllegalStateException("DolphinScheduler JDBC Registry is disconnected");
            }
            // A killed client's lock is removed by the upstream Registry purge
            // after session timeout, not immediately when the JVM disappears.
            // Wait for that purge rather than rejecting a safe restart at 1 second.
            long deadline =
                    System.nanoTime() + registrySessionTimeout.multipliedBy(2).toNanos();
            boolean acquired = false;
            while (System.nanoTime() < deadline) {
                if (!registry.isConnected()) {
                    throw new IllegalStateException("DolphinScheduler JDBC Registry connection was lost");
                }
                if (registry.acquireLock(REGISTRY_LOCK, 50)) {
                    acquired = true;
                    break;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while acquiring JDBC Registry lock", failure);
                }
            }
            if (!acquired) {
                throw new IllegalStateException("Timed out waiting for DolphinScheduler Registry execution lock");
            }
            registered = true;
            registry.put(REGISTRY_PATH, incarnation, true);
            incarnation();
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public String incarnation() {
        if (lock == null
                || !lock.isValid()
                || !registry.isConnected()
                || !incarnation.equals(registry.get(REGISTRY_PATH))) {
            throw new IllegalStateException("Embedded execution ownership was lost");
        }
        return incarnation;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        try {
            if (registered) {
                registered = false;
                try {
                    if (registry.isConnected() && incarnation.equals(registry.get(REGISTRY_PATH))) {
                        registry.delete(REGISTRY_PATH);
                    }
                } finally {
                    registry.releaseLock(REGISTRY_LOCK);
                }
            }
        } finally {
            try {
                if (lock != null && lock.isValid()) lock.release();
            } finally {
                if (channel != null) channel.close();
            }
        }
    }
}
