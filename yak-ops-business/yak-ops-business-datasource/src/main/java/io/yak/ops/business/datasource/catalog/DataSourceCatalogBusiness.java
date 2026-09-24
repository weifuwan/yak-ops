package io.yak.ops.business.datasource.catalog;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogDiagnosticsVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import java.util.List;

/**
 * 提供数据库、Schema、表和字段等 Catalog 元数据业务查询能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface DataSourceCatalogBusiness {

    DataSourceCatalogDiagnosticsVO queryDiagnostics();

    List<String> queryDatabases(Long dataSourceId);

    List<String> querySchemas(Long dataSourceId, String database);

    List<DataSourceCatalogTableVO> queryTables(Long dataSourceId, String database, String schema, String keyword);

    List<DataSourceCatalogTableVO> searchTables(
            Long dataSourceId, String database, String schema, String keyword, Integer limit);

    List<DataSourceCatalogColumnVO> queryColumns(Long dataSourceId, String database, String schema, String table);

    List<DataSourceCatalogOptionVO> queryTableOptions(Long dataSourceId);

    List<DataSourceCatalogOptionVO> queryTableOptions(Long dataSourceId, String matchMode, String keyword);
}
