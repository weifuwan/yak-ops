package io.yak.ops.plugin.registry.jdbc.repository;

import io.yak.ops.plugin.registry.jdbc.mapper.JdbcRegistryDataChangeEventMapper;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryDataChangeEvent;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryDataChangeEventDTO;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistryDataChangeEventRepository {

    private final JdbcRegistryDataChangeEventMapper jdbcRegistryDataChangeEventMapper;

    public JdbcRegistryDataChangeEventRepository(JdbcRegistryDataChangeEventMapper jdbcRegistryDataChangeEventMapper) {
        this.jdbcRegistryDataChangeEventMapper = jdbcRegistryDataChangeEventMapper;
    }

    public long getMaxJdbcRegistryDataChangeEventId() {
        Long maxId = jdbcRegistryDataChangeEventMapper.getMaxId();
        if (maxId == null) {
            return -1;
        } else {
            return maxId;
        }
    }

    public List<JdbcRegistryDataChangeEventDTO> selectJdbcRegistryDataChangeEventWhereIdAfter(long id) {
        return jdbcRegistryDataChangeEventMapper.selectJdbcRegistryDataChangeEventWhereIdAfter(id)
                .stream()
                .map(JdbcRegistryDataChangeEventDTO::fromJdbcRegistryDataChangeEvent)
                .collect(Collectors.toList());
    }

    public void insert(JdbcRegistryDataChangeEventDTO registryDataChangeEvent) {
        JdbcRegistryDataChangeEvent jdbcRegistryDataChangeEvent =
                JdbcRegistryDataChangeEventDTO.toJdbcRegistryDataChangeEvent(registryDataChangeEvent);
        jdbcRegistryDataChangeEventMapper.insert(jdbcRegistryDataChangeEvent);
        registryDataChangeEvent.setId(jdbcRegistryDataChangeEvent.getId());
    }

    public void deleteJdbcRegistryDataChangeEventBeforeCreateTime(Date createTime) {
        jdbcRegistryDataChangeEventMapper.deleteJdbcRegistryDataChangeEventBeforeCreateTime(createTime);
    }
}
