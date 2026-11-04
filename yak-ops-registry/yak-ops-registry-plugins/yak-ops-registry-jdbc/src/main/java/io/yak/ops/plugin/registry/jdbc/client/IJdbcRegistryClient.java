package io.yak.ops.plugin.registry.jdbc.client;

import io.yak.ops.plugin.registry.jdbc.model.DTO.DataType;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryDataDTO;
import io.yak.ops.plugin.registry.jdbc.server.ConnectionStateListener;
import io.yak.ops.plugin.registry.jdbc.server.JdbcRegistryDataChangeListener;

import java.util.List;
import java.util.Optional;

public interface IJdbcRegistryClient extends AutoCloseable {

    /**
     * Start the jdbc registry client, once started, the client will connect to the jdbc registry server, and then it can be used.
     */
    void start();

    /**
     * Get identify of the client.
     */
    JdbcRegistryClientIdentify getJdbcRegistryClientIdentify();

    /**
     * Check the connectivity of the client.
     */
    boolean isConnectivity();

    /**
     * Subscribe the jdbc registry connection state change event.
     */
    void subscribeConnectionStateChange(ConnectionStateListener connectionStateListener);

    /**
     * Subscribe the {@link JdbcRegistryDataDTO} change event.
     */
    void subscribeJdbcRegistryDataChange(JdbcRegistryDataChangeListener jdbcRegistryDataChangeListener);

    /**
     * Get the {@link JdbcRegistryDataDTO} by key.
     */
    Optional<JdbcRegistryDataDTO> getJdbcRegistryDataByKey(String key);

    /**
     * Put the {@link JdbcRegistryDataDTO} to the jdbc registry server.
     * <p>
     * If the key is already exist, then update the {@link JdbcRegistryDataDTO}. If the key is not exist, then insert a new {@link JdbcRegistryDataDTO}.
     */
    void putJdbcRegistryData(String key, String value, DataType dataType);

    /**
     * Delete the {@link JdbcRegistryDataDTO} by key.
     */
    void deleteJdbcRegistryDataByKey(String key);

    /**
     * List all the {@link JdbcRegistryDataDTO} children by key.
     * <p>
     * e.g. key = "/yak-ops/master", and data exist in db is "/yak-ops/master/master1", "/yak-ops/master/master2"
     * <p>
     * then the return value will be ["master1", "master2"]
     */
    List<JdbcRegistryDataDTO> listJdbcRegistryDataChildren(String key);

    /**
     * Check the key exist in the jdbc registry server.
     */
    boolean existJdbcRegistryDataKey(String key);

    /**
     * Acquire the jdbc registry lock by key. this is a blocking method. if you want to stop the blocking, you can use interrupt the thread.
     */
    void acquireJdbcRegistryLock(String lockKey) throws IllegalArgumentException;

    /**
     * Acquire the jdbc registry lock by key until timeout.
     */
    boolean acquireJdbcRegistryLock(String lockKey, long timeout);

    /**
     * Release the jdbc registry lock by key, if the lockKey is not exist will do nothing.
     */
    void releaseJdbcRegistryLock(String lockKey);

    /**
     * Close the jdbc registry client, once the client been closed, it cannot work anymore.
     */
    @Override
    void close();
}
