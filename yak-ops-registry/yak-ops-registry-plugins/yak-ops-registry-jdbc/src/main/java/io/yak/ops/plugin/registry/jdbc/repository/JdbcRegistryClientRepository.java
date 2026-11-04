package io.yak.ops.plugin.registry.jdbc.repository;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.plugin.registry.jdbc.mapper.JdbcRegistryClientHeartbeatMapper;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryClientHeartbeat;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryClientHeartbeatDTO;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistryClientRepository {

    @Autowired
    private JdbcRegistryClientHeartbeatMapper jdbcRegistryClientHeartbeatMapper;

    public List<JdbcRegistryClientHeartbeatDTO> queryAll() {
        return jdbcRegistryClientHeartbeatMapper.selectAll()
                .stream()
                .map(JdbcRegistryClientHeartbeatDTO::fromJdbcRegistryClientHeartbeat)
                .collect(Collectors.toList());
    }

    public void deleteByIds(Collection<Long> clientIds) {
        if (CollectionUtils.isEmpty(clientIds)) {
            return;
        }
        jdbcRegistryClientHeartbeatMapper.deleteBatchIds(clientIds);
    }

    public boolean updateById(JdbcRegistryClientHeartbeatDTO jdbcRegistryClientHeartbeatDTO) {
        JdbcRegistryClientHeartbeat jdbcRegistryClientHeartbeat =
                JdbcRegistryClientHeartbeatDTO.toJdbcRegistryClientHeartbeat(jdbcRegistryClientHeartbeatDTO);
        return jdbcRegistryClientHeartbeatMapper.updateById(jdbcRegistryClientHeartbeat) == 1;
    }

    public void insert(JdbcRegistryClientHeartbeatDTO jdbcRegistryClient) {
        checkNotNull(jdbcRegistryClient.getId());
        JdbcRegistryClientHeartbeat jdbcRegistryClientHeartbeat =
                JdbcRegistryClientHeartbeatDTO.toJdbcRegistryClientHeartbeat(jdbcRegistryClient);
        jdbcRegistryClientHeartbeatMapper.insert(jdbcRegistryClientHeartbeat);

    }
}
