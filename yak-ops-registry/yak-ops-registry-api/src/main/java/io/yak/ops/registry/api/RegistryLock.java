package io.yak.ops.registry.api;

/**
 * A handle to an acquired registry lock that releases the lock on {@link #close()},
 * intended for use with try-with-resources.
 * <p>
 * This handle is bound to the thread that acquired the lock — registry backends (e.g.
 * Zookeeper) keep per-thread state, so the handle must be closed on the same thread.
 * <p>
 * If {@link #close()} fails (the underlying release throws), the handle is left in
 * an un-closed state so the caller may retry releasing.
 */
public final class RegistryLock implements AutoCloseable {

    private final Registry registry;
    private final String lockKey;
    private boolean closed = false;

    RegistryLock(Registry registry, String lockKey) {
        this.registry = registry;
        this.lockKey = lockKey;
    }

    public String getLockKey() {
        return lockKey;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        registry.releaseLock(lockKey);
        closed = true;
    }
}
