package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;
import java.sql.Connection;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Uses the installed Doris datasource plugin for parameter validation and connections. */
@Component
public class DorisDataSourceAdapter {

    public DorisDataSourceContext open(DataSource datasource) throws Exception {
        if (datasource == null || datasource.getType() != DbType.DORIS) {
            throw new IllegalArgumentException("物理建表发布目前仅支持 Doris 数据源");
        }
        if (StringUtils.isBlank(datasource.getConnectionParams())) {
            throw new IllegalArgumentException("Doris 数据源连接配置不能为空");
        }

        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(DbType.DORIS);
        if (processor == null) {
            throw new IllegalStateException("Doris 数据源插件未加载");
        }
        BaseDataSourceParamDTO parameters =
                DataSourceUtils.buildDatasourceParamDTO(DbType.DORIS, datasource.getConnectionParams());
        if (parameters == null || StringUtils.isBlank(parameters.getDatabase())) {
            throw new IllegalArgumentException("Doris 数据源未配置数据库，请先在数据源管理中配置数据库");
        }
        DataSourceUtils.checkDatasourceParam(parameters);
        DataSourceCatalog catalog = processor.getCatalog();
        if (catalog == null) {
            throw new IllegalStateException("Doris 数据源插件未提供 Catalog 元数据能力");
        }

        ConnectionParam connectionParam =
                DataSourceUtils.buildConnectionParams(DbType.DORIS, datasource.getConnectionParams());
        Connection connection = processor.getConnection(connectionParam);
        if (connection == null) {
            throw new IllegalStateException("Doris 数据源连接创建失败");
        }
        return new DorisDataSourceContext(connection, catalog, parameters.getDatabase());
    }
}
