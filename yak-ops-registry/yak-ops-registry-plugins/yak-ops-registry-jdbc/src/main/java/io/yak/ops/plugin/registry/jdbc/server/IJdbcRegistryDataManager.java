package io.yak.ops.plugin.registry.jdbc.server;

import io.yak.ops.plugin.registry.jdbc.model.DTO.DataType;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryDataDTO;

import java.util.List;
import java.util.Optional;

public interface IJdbcRegistryDataManager {

    boolean existKey(String key);

    /**
     * Get all the {@link JdbcRegistryDataDTO}.
     */
    List<JdbcRegistryDataDTO> getAllJdbcRegistryData();

    /**
     * Get the {@link JdbcRegistryDataDTO} by key.
     */
    Optional<JdbcRegistryDataDTO> getRegistryDataByKey(String key);

    /**
     * List all the {@link JdbcRegistryDataDTO} children by key.
     * <p>
     * e.g. key = "/dolphinscheduler/master", and data exist in db is "/dolphinscheduler/master/master1", "/dolphinscheduler/master/master2"
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
}
