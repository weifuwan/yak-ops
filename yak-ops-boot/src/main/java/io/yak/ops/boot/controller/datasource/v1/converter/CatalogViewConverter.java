package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogDiagnostics;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogDiagnosticsVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import org.springframework.stereotype.Component;

/**
 * 将插件 Catalog 元数据转换为稳定的 HTTP 展示结构。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Component
@ConditionalOnDataSourceEnabled
public class CatalogViewConverter {

    public DataSourceCatalogTableVO table(DataSourceTable value) {
        return new DataSourceCatalogTableVO(
                value.getDatabase(), value.getSchema(), value.getName(), value.getType(), value.getRemarks());
    }

    public DataSourceCatalogColumnVO column(DataSourceColumn value) {
        return new DataSourceCatalogColumnVO(
                value.getName(),
                value.getTypeName(),
                value.getJdbcType(),
                value.getSize(),
                value.getScale(),
                value.isNullable(),
                value.getOrdinalPosition(),
                value.isPrimaryKey(),
                value.getRemarks());
    }

    public DataSourceCatalogOptionVO option(DataSourceTable value) {
        String label = isBlank(value.getRemarks()) ? value.getName() : value.getRemarks();
        return new DataSourceCatalogOptionVO(value.getName(), label, value.getRemarks());
    }

    public DataSourceCatalogDiagnosticsVO diagnostics(DataSourceCatalogDiagnostics.Snapshot snapshot) {
        var operations = snapshot.operations().stream()
                .map(operation -> new DataSourceCatalogDiagnosticsVO.OperationVO(
                        operation.operation(),
                        operation.total(),
                        operation.failures(),
                        operation.slow(),
                        operation.averageDurationMs(),
                        operation.maxDurationMs(),
                        operation.lastSlowDurationMs(),
                        operation.lastSlowTime()))
                .toList();
        return new DataSourceCatalogDiagnosticsVO(
                snapshot.cacheHits(), snapshot.cacheMisses(), snapshot.cacheHitRate(), operations);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
