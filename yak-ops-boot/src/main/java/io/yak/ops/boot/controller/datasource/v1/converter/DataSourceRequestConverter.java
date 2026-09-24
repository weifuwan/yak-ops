package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.connection.DataSourceConnectionRequest;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.management.DataSourceConfigurationCommand;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
public class DataSourceRequestConverter {
    public DataSourceConfigurationCommand configuration(DataSourceDTO dto) {
        if (dto == null) throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源参数不能为空");
        return new DataSourceConfigurationCommand(
                normalizeName(dto.getName()),
                parseDbType(dto.getDbType()),
                parseEnvironment(dto.getEnvironment()),
                normalizeNullable(dto.getRemark()),
                dto.getConnectionParams());
    }

    public DataSourceQuery query(DataSourceQueryDTO dto) {
        if (dto == null) throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "分页查询参数不能为空");
        return new DataSourceQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                normalizeNullable(dto.getName()),
                normalizeNullable(dto.getKeyword()),
                StringUtils.hasText(dto.getDbType()) ? parseDbType(dto.getDbType()) : null,
                StringUtils.hasText(dto.getEnvironment()) ? parseEnvironment(dto.getEnvironment()) : null,
                StringUtils.hasText(dto.getConnStatus()) ? parseConnectionStatus(dto.getConnStatus()) : null);
    }

    public DataSourceDbType optionalDbType(String value) {
        return StringUtils.hasText(value) ? parseDbType(value) : null;
    }

    public DataSourceConnectionRequest connectionTest(DataSourceConnectTestDTO dto) {
        if (dto == null) throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接测试参数不能为空");
        return new DataSourceConnectionRequest(
                dto.getDataSourceId(),
                StringUtils.hasText(dto.getDbType()) ? parseDbType(dto.getDbType()) : null,
                dto.getConnJson());
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value))
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据源名称不能为空");
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
}
