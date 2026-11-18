package io.yak.ops.api.service;

import io.yak.ops.api.vo.catalog.CatalogChildrenPageVO;
import io.yak.ops.api.vo.catalog.CatalogNodeType;
import io.yak.ops.api.vo.catalog.CatalogTreeNodeVO;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.datasource.api.catalog.CatalogCapabilities;
import io.yak.ops.plugin.datasource.api.catalog.CatalogIndex;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.spi.enums.DbType;

import java.util.List;

/** Application service exposing datasource driver properties and unified catalog metadata. */
public interface DataSourceCatalogService {

    List<ConnectionProperty> getConnectionProperties(DbType dbType);

    CatalogCapabilities getCapabilities(User loginUser, long projectCode, Integer datasourceId);

    CatalogChildrenPageVO getChildren(User loginUser, long projectCode, Integer datasourceId,
                                      CatalogNodeType nodeType, CatalogPath path, String keyword,
                                      Integer pageNo, Integer pageSize);

    List<CatalogTreeNodeVO> search(User loginUser, long projectCode, Integer datasourceId,
                                   String keyword, Integer limit);

    CatalogObject getObject(User loginUser, long projectCode, Integer datasourceId, CatalogPath path);

    List<DataSourceCatalogColumn> getColumns(User loginUser, long projectCode,
                                             Integer datasourceId, CatalogPath path);

    List<CatalogIndex> getIndexes(User loginUser, long projectCode, Integer datasourceId, CatalogPath path);
}
