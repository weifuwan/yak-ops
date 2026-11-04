package io.yak.ops.plugin.registry.jdbc.repository;

import io.yak.ops.plugin.registry.jdbc.mapper.JdbcRegistryDataMapper;
import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryData;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryDataDTO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistryDataRepository {

    @Autowired
    private JdbcRegistryDataMapper jdbcRegistryDataMapper;

    public List<JdbcRegistryDataDTO> selectAll() {
        return jdbcRegistryDataMapper
                .selectAll()
                .stream()
                .map(JdbcRegistryDataDTO::fromJdbcRegistryData)
                .collect(Collectors.toList());
    }

    public Optional<JdbcRegistryDataDTO> selectByKey(String key) {
        return Optional.ofNullable(jdbcRegistryDataMapper.selectByKey(key))
                .map(JdbcRegistryDataDTO::fromJdbcRegistryData);
    }

    public void deleteByKey(String key) {
        jdbcRegistryDataMapper.deleteByKey(key);
    }

    public void insert(JdbcRegistryDataDTO jdbcRegistryData) {
        JdbcRegistryData jdbcRegistryDataDO = JdbcRegistryDataDTO.toJdbcRegistryData(jdbcRegistryData);
        jdbcRegistryDataMapper.insert(jdbcRegistryDataDO);
        jdbcRegistryData.setId(jdbcRegistryDataDO.getId());
    }

    public void updateById(JdbcRegistryDataDTO jdbcRegistryDataDTO) {
        jdbcRegistryDataMapper.updateById(JdbcRegistryDataDTO.toJdbcRegistryData(jdbcRegistryDataDTO));
    }
}
