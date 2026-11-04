package io.yak.ops.plugin.registry.jdbc.server;

public interface IJdbcRegistryLockManager {

    /**
     * Acquire the jdbc registry lock by key. this is a blocking method. if you want to stop the blocking, you can use interrupt the thread.
     */
    void acquireJdbcRegistryLock(Long clientId, String lockKey) throws InterruptedException;

    /**
     * Acquire the jdbc registry lock by key until timeout.
     */
    boolean acquireJdbcRegistryLock(Long clientId, String lockKey, long timeout);

    /**
     * Release the jdbc registry lock by key, if the lockKey is not exist will do nothing.
     */
    void releaseJdbcRegistryLock(Long clientId, String lockKey);

}
