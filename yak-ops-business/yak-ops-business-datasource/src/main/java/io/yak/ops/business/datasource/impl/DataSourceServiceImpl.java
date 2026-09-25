package io.yak.ops.business.datasource.impl;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.datasource.DataSourceEntityRepository;
import io.yak.ops.dao.repository.datasource.DataSourcePageQuery;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 实现数据源配置生命周期、查询、连接测试和 Entity → VO 转换。
 *
 * <p>负责业务校验与持久化编排；数据库类型差异、连接参数解析和敏感信息处理统一委托给内部 DataSourcePluginRegistry。</p>
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceServiceImpl.class);

    @Resource
    private DataSourceEntityRepository repository;

    @Resource
    private DataSourcePluginRegistry pluginRegistry;

    @Resource
    private DataSourceProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addDataSource(DataSourceDTO dto) {
        requireDataSourceDto(dto);
        String name = normalizeName(dto.getName());
        String dbType = pluginRegistry.resolvePluginType(dto.getDbType());
        ensureNameAvailable(name, null);
        var connection = pluginRegistry.parseConnection(dbType, dto.getConnectionParams());

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
        LOG.info("数据源创建完成，dataSourceId={}, type={}", entity.getId(), entity.getDbType());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDataSource(String id, DataSourceDTO dto) {
        requireDataSourceDto(dto);
        DataSourceEntity existing = requireEntity(id);
        String name = normalizeName(dto.getName());
        String dbType = pluginRegistry.resolvePluginType(dto.getDbType());
        ensureNameAvailable(name, id);
        if (!existing.getDbType().equals(dbType)) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "编辑数据源时不允许修改数据源类型");
        }

        var connection =
                pluginRegistry.mergeStoredSecrets(existing.getDbType(), dto.getConnectionParams(), existing.getConnectionParams());
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
        LOG.info("数据源更新完成，dataSourceId={}, type={}", existing.getId(), existing.getDbType());
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
        LOG.info("数据源删除完成，dataSourceId={}, type={}", existing.getId(), existing.getDbType());
        return true;
    }

    @Override
    public PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto) {
        if (dto == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "分页查询参数不能为空");
        }
        if (dto.getSorts() != null && !dto.getSorts().isEmpty()) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源分页暂不支持自定义排序");
        }
        DataSourcePageQuery query = new DataSourcePageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                normalizeNullable(dto.getName()),
                normalizeNullable(dto.getKeyword()),
                StringUtils.hasText(dto.getDbType()) ? pluginRegistry.resolvePluginType(dto.getDbType()) : null,
                StringUtils.hasText(dto.getEnvironment()) ? parseEnvironment(dto.getEnvironment()) : null,
                StringUtils.hasText(dto.getConnStatus()) ? parseConnectionStatus(dto.getConnStatus()) : null);
        return PagingData.from(repository.queryPage(query).map(value -> toDataSourceVO(value, false)));
    }

    @Override
    public boolean testConnection(String id) {
        DataSourceEntity entity = requireEntity(id);
        try {
            pluginRegistry.testConnection(entity.getDbType(), entity.getConnectionParams(), connectionTestTimeoutSeconds());
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
        String dbType;
        String connectionJson = dto.getConnJson();

        if (existing != null) {
            dbType = existing.getDbType();
            if (StringUtils.hasText(dto.getDbType()) && !pluginRegistry.resolvePluginType(dto.getDbType()).equals(dbType)) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_DB_TYPE, "连接测试的数据源类型与已保存数据源不一致");
            }
            connectionJson = pluginRegistry
                    .mergeStoredSecrets(dbType, connectionJson, existing.getConnectionParams())
                    .normalizedJson();
        } else {
            dbType = StringUtils.hasText(dto.getDbType())
                    ? pluginRegistry.resolvePluginType(dto.getDbType())
                    : pluginRegistry.resolveConnectionType(connectionJson);
        }

        try {
            pluginRegistry.testConnection(dbType, connectionJson, connectionTestTimeoutSeconds());
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
        return Math.max(1, properties.getConnectionTestTimeoutSeconds());
    }

    private DataSourceVO toDataSourceVO(DataSourceEntity source, boolean includeOriginalJson) {
        if (source == null) {
            return null;
        }
        DataSourceVO target = new DataSourceVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setDbType(source.getDbType());
        target.setJdbcUrl(pluginRegistry.maskSensitiveText(source.getJdbcUrl()));
        target.setEnvironment(source.getEnvironment() == null ? null : source.getEnvironment().name());
        target.setEnvironmentName(
                source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
        target.setConnStatus(source.getConnStatus() == null ? null : source.getConnStatus().name());
        target.setRemark(source.getRemark());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        if (includeOriginalJson && source.getDbType() != null) {
            target.setOriginalJson(pluginRegistry.maskConnectionJson(source.getDbType(), source.getOriginalJson()));
        }
        return target;
    }
}
