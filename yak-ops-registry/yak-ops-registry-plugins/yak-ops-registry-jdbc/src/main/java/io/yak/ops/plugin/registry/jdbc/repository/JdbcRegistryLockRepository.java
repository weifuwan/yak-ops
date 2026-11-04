package io.yak.ops.plugin.registry.jdbc.repository;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.plugin.registry.jdbc.mapper.JdbcRegistryLockMapper;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryLock;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryLockDTO;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistryLockRepository {

    @Autowired
    private JdbcRegistryLockMapper jdbcRegistryLockMapper;

    public List<JdbcRegistryLockDTO> queryAll() {
        return jdbcRegistryLockMapper.selectList(null)
                .stream()
                .map(JdbcRegistryLockDTO::fromJdbcRegistryLock)
                .collect(Collectors.toList());
    }

    public void insert(JdbcRegistryLockDTO jdbcRegistryLock) {
        checkNotNull(jdbcRegistryLock);
        JdbcRegistryLock jdbcRegistryLockDO = JdbcRegistryLockDTO.toJdbcRegistryLock(jdbcRegistryLock);
        jdbcRegistryLockMapper.insert(jdbcRegistryLockDO);
        jdbcRegistryLock.setId(jdbcRegistryLockDO.getId());
    }

    public void deleteById(Long id) {
        jdbcRegistryLockMapper.deleteById(id);
    }
}
