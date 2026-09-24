package io.yak.ops.boot.controller.datasource.v1.converter;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogDiagnostics;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogDiagnosticsVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceQueryResultVO;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
public class CatalogViewConverter {
  public DataSourceCatalogTableVO table(CatalogTable value) { return new DataSourceCatalogTableVO(value.database(), value.schema(), value.name(), value.type(), value.remarks()); }
  public DataSourceCatalogColumnVO column(CatalogColumn value) { return new DataSourceCatalogColumnVO(value.name(), value.typeName(), value.jdbcType(), value.size(), value.scale(), value.nullable(), value.ordinalPosition(), value.primaryKey(), value.remarks()); }
  public DataSourceCatalogColumnOptionVO columnOption(CatalogColumn value) { return new DataSourceCatalogColumnOptionVO(value.ordinalPosition(), value.name(), value.typeName(), value.ordinalPosition(), value.nullable() ? "YES" : "NO", value.remarks(), value.primaryKey() ? "PRI" : ""); }
  public DataSourceCatalogOptionVO option(CatalogTable value) { String label = isBlank(value.remarks()) ? value.name() : value.remarks(); return new DataSourceCatalogOptionVO(value.name(), label, value.remarks()); }
  public DataSourceQueryResultVO preview(CatalogQueryResult result) { var columns = result.columns().stream().map(column -> new DataSourcePreviewColumnVO(column.title(), column.dataIndex(), column.key(), column.ellipsis())).toList(); return new DataSourceQueryResultVO(columns, result.rows(), result.total()); }
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
        snapshot.cacheHits(),
        snapshot.cacheMisses(),
        snapshot.cacheHitRate(),
        operations);
  }
  private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
