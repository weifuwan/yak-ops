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

    List<String> queryDatabases(String dataSourceId);

    List<String> querySchemas(String dataSourceId, String database);

    List<DataSourceCatalogTableVO> queryTables(String dataSourceId, String database, String schema, String keyword);

    List<DataSourceCatalogTableVO> searchTables(
            String dataSourceId, String database, String schema, String keyword, Integer limit);

    List<DataSourceCatalogColumnVO> queryColumns(String dataSourceId, String database, String schema, String table);

    List<DataSourceCatalogOptionVO> queryTableOptions(String dataSourceId);

    List<DataSourceCatalogOptionVO> queryTableOptions(String dataSourceId, String matchMode, String keyword);
}
