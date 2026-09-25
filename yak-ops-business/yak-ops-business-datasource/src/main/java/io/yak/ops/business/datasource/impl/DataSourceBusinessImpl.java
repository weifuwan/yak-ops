package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceBusiness;
import io.yak.ops.business.datasource.DataSourceChangedEvent;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginBusiness;
import io.yak.ops.common.PageData;
import io.yak.ops.common.PagingData;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 负责数据源配置生命周期、列表查询和连接测试，并将 DAO 持久化对象收口在 Business 内部。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Service
@ConditionalOnDataSourceEnabled
public class DataSourceBusinessImpl implements DataSourceBusiness {

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourcePluginBusiness pluginBusiness;

    @Resource
    private DataSourceProperties properties;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDataSource(DataSourceDTO dto) {
        requireDataSourceDto(dto);
        String name = normalizeName(dto.getName());
        DataSourceDbType dbType = parseDbType(dto.getDbType());
        ensureNameAvailable(name, null);
        var connection = pluginBusiness.parseConnection(dbType, dto.getConnectionParams());

        DataSourceEntity entity = new DataSourceEntity();
        entity.setName(name);
        entity.setDbType(dbType);
        entity.setJdbcUrl(connection.jdbcUrl());
        entity.setEnvironment(parseEnvironment(dto.getEnvironment()));
        entity.setConnStatus(DataSourceConnStatus.UNKNOWN);
        entity.setRemark(normalizeNullable(dto.getRemark()));
        entity.setConnectionParams(connection.normalizedJson());
        entity.setOriginalJson(connection.normalizedJson());
        entity.initCreate();
        if (repository.add(entity) == null) {
            throw new DataSourceException(DataSourceErrorCode.CREATE_FAILED);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDataSource(String id, DataSourceDTO dto) {
        requireDataSourceDto(dto);
        DataSourceEntity existing = requireEntity(id);
        String name = normalizeName(dto.getName());
        DataSourceDbType dbType = parseDbType(dto.getDbType());
        ensureNameAvailable(name, id);
        if (existing.getDbType() != dbType) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "编辑数据源时不允许修改数据源类型");
        }

        var connection =
                pluginBusiness.mergeStoredSecrets(existing.getDbType(), dto.getConnectionParams(), existing.getConnectionParams());
        existing.setName(name);
        existing.setJdbcUrl(connection.jdbcUrl());
        existing.setEnvironment(parseEnvironment(dto.getEnvironment()));
        existing.setConnStatus(DataSourceConnStatus.UNKNOWN);
        existing.setRemark(normalizeNullable(dto.getRemark()));
        existing.setConnectionParams(connection.normalizedJson());
        existing.setOriginalJson(connection.normalizedJson());
        existing.initUpdate();

        if (repository.update(existing) == null) {
            throw new DataSourceException(DataSourceErrorCode.UPDATE_FAILED);
        }
        eventPublisher.publishEvent(new DataSourceChangedEvent(id));
        return true;
    }

    @Override
    public DataSourceVO queryDataSource(String id) {
        return toDataSourceVO(requireEntity(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDataSource(String id) {
        DataSourceEntity existing = requireEntity(id);
        if (repository.deleteById(existing.getId()) <= 0) {
            throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
        }
        eventPublisher.publishEvent(new DataSourceChangedEvent(existing.getId()));
        return true;
    }

    @Override
    public PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "分页查询参数不能为空");
        }
        DataSourceEntityRepository.PageQuery query = new DataSourceEntityRepository.PageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                normalizeNullable(dto.getName()),
                normalizeNullable(dto.getKeyword()),
                StringUtils.hasText(dto.getDbType()) ? parseDbType(dto.getDbType()) : null,
                StringUtils.hasText(dto.getEnvironment()) ? parseEnvironment(dto.getEnvironment()) : null,
                StringUtils.hasText(dto.getConnStatus()) ? parseConnectionStatus(dto.getConnStatus()) : null);
        return PagingData.from(repository.queryPage(query).map(value -> toDataSourceVO(value, false)));
    }

    @Override
    public DataSourceSummaryVO queryDataSourceSummary() {
        return toSummaryVO(repository.querySummary());
    }

    @Override
    public PagingData<DataSourceVO> queryAllDataSources() {
        List<DataSourceVO> records = repository.queryAll(null).stream()
                .map(value -> toDataSourceVO(value, false))
                .toList();
        long pages = records.isEmpty() ? 0L : 1L;
        long pageSize = Math.max(1, records.size());
        return PagingData.from(new PageData<>(records, records.size(), pages, 1L, pageSize));
    }

    @Override
    public List<DataSourceOptionVO> queryDataSourceOptions(String dbType) {
        DataSourceDbType type = StringUtils.hasText(dbType) ? parseDbType(dbType) : null;
        return repository.queryAll(type).stream().map(this::toOptionVO).toList();
    }

    @Override
    public boolean testConnection(String id) {
        DataSourceEntity entity = requireEntity(id);
        try {
            pluginBusiness.testConnection(entity.getDbType(), entity.getConnectionParams(), connectionTestTimeoutSeconds());
            entity.setConnStatus(DataSourceConnStatus.CONNECTED);
            entity.initUpdate();
            repository.update(entity);
            return true;
        } catch (RuntimeException exception) {
            DataSourceException mapped = connectException(exception);
            if (DataSourceErrorCode.CONNECT_FAILED.equals(mapped.getErrorCode())) {
                entity.setConnStatus(DataSourceConnStatus.DISCONNECTED);
                entity.initUpdate();
                repository.update(entity);
            }
            throw mapped;
        }
    }

    @Override
    public boolean testConnection(DataSourceConnectTestDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接测试参数不能为空");
        }

        DataSourceEntity existing = dto.getDataSourceId() == null ? null : requireEntity(dto.getDataSourceId());
        DataSourceDbType dbType;
        String connectionJson = dto.getConnJson();

        if (existing != null) {
            dbType = existing.getDbType();
            if (StringUtils.hasText(dto.getDbType()) && parseDbType(dto.getDbType()) != dbType) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接测试的数据源类型与已保存数据源不一致");
            }
            connectionJson = pluginBusiness
                    .mergeStoredSecrets(dbType, connectionJson, existing.getConnectionParams())
                    .normalizedJson();
        } else {
            dbType = StringUtils.hasText(dto.getDbType())
                    ? parseDbType(dto.getDbType())
                    : pluginBusiness.resolveConnectionType(connectionJson);
        }

        try {
            pluginBusiness.testConnection(dbType, connectionJson, connectionTestTimeoutSeconds());
            return true;
        } catch (RuntimeException exception) {
            throw connectException(exception);
        }
    }

    private DataSourceEntity requireEntity(String id) {
        if (!StringUtils.hasText(id)) {
            throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
        }
        return repository.queryById(id).orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
    }

    private void ensureNameAvailable(String name, String excludeId) {
        if (repository.existsByName(name, excludeId)) {
            throw new DataSourceException(DataSourceErrorCode.DUPLICATE_NAME);
        }
    }

    private void requireDataSourceDto(DataSourceDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源参数不能为空");
        }
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源名称不能为空");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DataSourceDbType parseDbType(String value) {
        try {
            return DataSourceDbType.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
        }
    }

    private DataSourceEnvironment parseEnvironment(String value) {
        try {
            return DataSourceEnvironment.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_ENVIRONMENT, exception.getMessage(), exception);
        }
    }

    private DataSourceConnStatus parseConnectionStatus(String value) {
        try {
            return DataSourceConnStatus.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(
                    DataSourceErrorCode.INVALID_CONNECTION_STATUS, "不支持的连接状态：" + value, exception);
        }
    }

    private DataSourceException connectException(RuntimeException exception) {
        if (exception instanceof DataSourceException dataSourceException) {
            return dataSourceException;
        }
        return new DataSourceException(DataSourceErrorCode.CONNECT_FAILED, exception.getMessage(), exception);
    }

    private int connectionTestTimeoutSeconds() {
        return Math.max(1, properties.getConnectionTest().getTimeoutSeconds());
    }

    private DataSourceVO toDataSourceVO(DataSourceEntity source, boolean includeOriginalJson) {
        if (source == null) {
            return null;
        }
        DataSourceVO target = new DataSourceVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setDbType(source.getDbType() == null ? null : source.getDbType().name());
        target.setJdbcUrl(pluginBusiness.maskSensitiveText(source.getJdbcUrl()));
        target.setEnvironment(source.getEnvironment() == null ? null : source.getEnvironment().name());
        target.setEnvironmentName(
                source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
        target.setConnStatus(source.getConnStatus() == null ? null : source.getConnStatus().name());
        target.setRemark(source.getRemark());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        if (includeOriginalJson && source.getDbType() != null) {
            target.setOriginalJson(pluginBusiness.maskConnectionJson(source.getDbType(), source.getOriginalJson()));
        }
        return target;
    }

    private DataSourceOptionVO toOptionVO(DataSourceEntity source) {
        return new DataSourceOptionVO(
                source.getName(),
                String.valueOf(source.getId()),
                source.getDbType() == null ? null : source.getDbType().name());
    }

    private DataSourceSummaryVO toSummaryVO(DataSourceSummaryRow source) {
        if (source == null) {
            return new DataSourceSummaryVO(0L, 0L, 0L, 0L, 0L);
        }
        return new DataSourceSummaryVO(
                source.getTotal(),
                source.getConnected(),
                source.getDisconnected(),
                source.getUnknown(),
                source.getEnvironmentCount());
    }
}
