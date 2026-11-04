package io.yak.ops.plugin.registry.jdbc.server;

import io.yak.ops.plugin.registry.jdbc.client.IJdbcRegistryClient;
import io.yak.ops.plugin.registry.jdbc.model.DTO.DataType;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryDataDTO;

import java.util.List;
import java.util.Optional;

/**
 * The JdbcRegistryServer is represent the server side of the jdbc registry, it can be thought as db server.
 */
public interface IJdbcRegistryServer extends AutoCloseable {

    void start();

    /**
     * Register a client to the server, once the client connect to the server then the server will refresh the client's term interval.
     */
    void registerClient(IJdbcRegistryClient jdbcRegistryClient);

    /**
     * Deregister a client to the server, once the client id deregister, then the server will deleted the data related to the client and stop refresh the client's term.
     */
    void deregisterClient(IJdbcRegistryClient jdbcRegistryClient);

    /**
     * Get the {@link JdbcRegistryServerState}
     */
    JdbcRegistryServerState getServerState();

    /**
     * Subscribe the jdbc registry connection state change
     */
    void subscribeConnectionStateChange(ConnectionStateListener connectionStateListener);

    /**
     * Subscribe the {@link io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryData} change.
     */
    void subscribeJdbcRegistryDataChange(JdbcRegistryDataChangeListener jdbcRegistryDataChangeListener);

    /**
     * Check the jdbc registry data key is exist or not.
     */
    boolean existJdbcRegistryDataKey(String key);

    /**
     * Get the {@link JdbcRegistryDataDTO} by key.
     */
    Optional<JdbcRegistryDataDTO> getJdbcRegistryDataByKey(String key);

    /**
     * List all the {@link JdbcRegistryDataDTO} children by key.
     * <p>
     * e.g. key = "/yak-ops/master", and data exist in db is "/yak-ops/master/master1", "/yak-ops/master/master2"
     * <p>
     * then the return value will be ["master1", "master2"]
     */
    List<JdbcRegistryDataDTO> listJdbcRegistryDataChildren(String key);

    /**
     * Put the {@link JdbcRegistryDataDTO} to the jdbc registry server.
     * <p>
     * If the key is already exist, then update the {@link JdbcRegistryDataDTO}. If the key is not exist, then insert a new {@link JdbcRegistryDataDTO}.
     */
    void putJdbcRegistryData(Long clientId, String key, String value, DataType dataType);

    /**
     * Delete the {@link JdbcRegistryDataDTO} by key.
     */
    void deleteJdbcRegistryDataByKey(String key);

    /**
     * Acquire the jdbc registry lock by key. this is a blocking method. if you want to stop the blocking, you can use interrupt the thread.
     */
    void acquireJdbcRegistryLock(Long clientId, String key);

    /**
     * Acquire the jdbc registry lock by key until timeout.
     */
    boolean acquireJdbcRegistryLock(Long clientId, String key, long timeout);

    /**
     * Release the jdbc registry lock by key, if the lockKey is not exist will do nothing.
     */
    void releaseJdbcRegistryLock(Long clientId, String key);

    /**
     * Close the server, once the server been closed, it cannot work anymore.
     */
    @Override
    void close();
}
