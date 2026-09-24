package io.yak.ops.business.datasource.repository;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.PageData;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository.PageQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 连接 Datasource 领域仓储与 DAO 持久化仓储，负责领域对象和 Entity 的双向转换。
 *
 * @author weifuwan
 * @since 2026-08-09
 */
@Repository
@ConditionalOnDataSourceEnabled
public class DataSourceRepositoryAdapter implements DataSourceRepository {

    @Resource
    private DataSourceEntityRepository entityRepository;

    @Override
    public Optional<DataSourceDefinition> findById(Long id) {
        if (id == null) return Optional.empty();
        return entityRepository.queryById(id).map(this::toDomain);
    }

    @Override
    public boolean insert(DataSourceDefinition definition) {
        entityRepository.add(toEntity(definition));
        return true;
    }

    @Override
    public boolean update(DataSourceDefinition definition) {
        entityRepository.update(toEntity(definition));
        return true;
    }

    @Override
    public boolean delete(Long id) {
        return id != null && entityRepository.deleteById(id) > 0;
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        return entityRepository.existsByName(name, excludeId);
    }

    @Override
    public PageData<DataSourceDefinition> page(DataSourceQuery query) {
        DataSourceQuery condition =
                query == null ? new DataSourceQuery(1, 10, null, null, null, null, null) : query;
        return entityRepository
                .queryPage(new PageQuery(
                        condition.pageNo(),
                        condition.pageSize(),
                        condition.name(),
                        condition.keyword(),
                        condition.dbType(),
                        condition.environment(),
                        condition.connStatus()))
                .map(this::toDomain);
    }

    @Override
    public List<DataSourceDefinition> findAll(DataSourceDbType dbType) {
        return entityRepository.queryAll(dbType).stream().map(this::toDomain).toList();
    }

    @Override
    public DataSourceSummary summary() {
        DataSourceSummaryRow row = entityRepository.querySummary();
        return row == null
                ? DataSourceSummary.empty()
                : new DataSourceSummary(
                        row.getTotal(),
                        row.getConnected(),
                        row.getDisconnected(),
                        row.getUnknown(),
                        row.getEnvironmentCount());
    }

    @Override
    public boolean updateConnectionStatus(Long id, DataSourceConnStatus status) {
        return entityRepository.updateConnectionStatus(id, status);
    }

    private DataSourceDefinition toDomain(DataSourceEntity entity) {
        if (entity == null) return null;
        return DataSourceDefinition.restore(
                entity.getId(),
                entity.getName(),
                entity.getDbType(),
                entity.getJdbcUrl(),
                entity.getEnvironment(),
                entity.getConnStatus(),
                entity.getRemark(),
                entity.getConnectionParams(),
                entity.getOriginalJson(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    private DataSourceEntity toEntity(DataSourceDefinition definition) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(definition.getId());
        entity.setName(definition.getName());
        entity.setDbType(definition.getDbType());
        entity.setJdbcUrl(definition.getJdbcUrl());
        entity.setEnvironment(definition.getEnvironment());
        entity.setConnStatus(definition.getConnStatus());
        entity.setRemark(definition.getRemark());
        entity.setConnectionParams(definition.getConnectionParams());
        entity.setOriginalJson(definition.getOriginalJson());
        entity.setCreateTime(definition.getCreateTime());
        entity.setUpdateTime(definition.getUpdateTime());
        return entity;
    }
}
