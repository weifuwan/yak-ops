package io.yak.ops.business.datasource.management;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.PageData;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一负责数据源配置的新增、编辑、删除和查询，不再维护与 DAO Entity 重复的 Domain 模型。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceManager {

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Transactional(transactionManager = "opsDataSourceTransactionManager", rollbackFor = Exception.class)
    public boolean create(DataSourceConfigurationCommand command) {
        ensureNameAvailable(command.name(), null);
        var connection = pluginRegistry.parseConnection(command.dbType(), command.connectionJson());

        DataSourceEntity entity = new DataSourceEntity();
        entity.setName(command.name());
        entity.setDbType(command.dbType());
        entity.setJdbcUrl(connection.jdbcUrl());
        entity.setEnvironment(command.environment());
        entity.setConnStatus(DataSourceConnStatus.UNKNOWN);
        entity.setRemark(command.remark());
        entity.setConnectionParams(connection.normalizedJson());
        entity.setOriginalJson(connection.normalizedJson());
        if (repository.add(entity) == null) {
            throw new DataSourceException(DataSourceErrorCode.CREATE_FAILED);
        }
        return true;
    }

    @Transactional(transactionManager = "opsDataSourceTransactionManager", rollbackFor = Exception.class)
    public boolean update(Long id, DataSourceConfigurationCommand command) {
        DataSourceEntity existing = require(id);
        ensureNameAvailable(command.name(), id);
        if (existing.getDbType() != command.dbType()) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "编辑数据源时不允许修改数据源类型");
        }

        var connection = pluginRegistry.mergeStoredSecrets(
                existing.getDbType(), command.connectionJson(), existing.getConnectionParams());
        existing.setName(command.name());
        existing.setJdbcUrl(connection.jdbcUrl());
        existing.setEnvironment(command.environment());
        existing.setConnStatus(DataSourceConnStatus.UNKNOWN);
        existing.setRemark(command.remark());
        existing.setConnectionParams(connection.normalizedJson());
        existing.setOriginalJson(connection.normalizedJson());

        if (repository.update(existing) == null) {
            throw new DataSourceException(DataSourceErrorCode.UPDATE_FAILED);
        }
        eventPublisher.publishEvent(new DataSourceChangedEvent(id));
        return true;
    }

    @Transactional(transactionManager = "opsDataSourceTransactionManager", rollbackFor = Exception.class)
    public boolean delete(Long id) {
        DataSourceEntity existing = require(id);
        if (repository.deleteById(existing.getId()) <= 0) {
            throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
        }
        eventPublisher.publishEvent(new DataSourceChangedEvent(existing.getId()));
        return true;
    }

    public DataSourceEntity require(Long id) {
        if (id == null || id <= 0L) throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
        return repository.queryById(id).orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
    }

    public PageData<DataSourceEntity> page(DataSourceEntityRepository.PageQuery query) {
        return repository.queryPage(query);
    }

    public DataSourceSummaryRow summary() {
        return repository.querySummary();
    }

    public List<DataSourceEntity> findAll(DataSourceDbType dbType) {
        return repository.queryAll(dbType);
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        if (repository.existsByName(name, excludeId)) {
            throw new DataSourceException(DataSourceErrorCode.DUPLICATE_NAME);
        }
    }
}
