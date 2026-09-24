package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Safety policy for catalog preview/count/describe reads. */
@Component
@ConditionalOnDataSourceEnabled
public class CatalogReadPolicy {

    private static final Pattern READ_ONLY_SELECT = Pattern.compile("(?is)^SELECT\\b.*");

    public void validateReadOnly(CatalogReadRequest request) {
        if (request == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "Catalog 读取请求不能为空");
        }
        if (!request.sqlMode()) return;
        String normalized = request.sql().trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.indexOf(';') >= 0
                || !READ_ONLY_SELECT.matcher(normalized).matches()) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "数据预览仅允许执行单条 SELECT 查询");
        }
    }

    public void requireSql(CatalogReadRequest request) {
        if (request == null || request.sql() == null) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "query 不能为空");
        }
    }
}
