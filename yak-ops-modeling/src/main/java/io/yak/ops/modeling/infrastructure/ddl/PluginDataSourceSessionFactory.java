package io.yak.ops.modeling.infrastructure.ddl;

import io.yak.ops.modeling.ddl.api.DdlSessionFactory;
import io.yak.ops.modeling.ddl.session.DdlConnectionInfo;
import io.yak.ops.modeling.ddl.session.DdlSession;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalog;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Creates DDL sessions through the installed datasource plugin registry. */
@Component
public class PluginDataSourceSessionFactory implements DdlSessionFactory {

    @Override
    public DdlSession open(DdlConnectionInfo connectionInfo) throws Exception {
        if (connectionInfo == null || StringUtils.isBlank(connectionInfo.getDatabaseType())) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        if (StringUtils.isBlank(connectionInfo.getConnectionParams())) {
            throw new IllegalArgumentException("数据源连接配置不能为空");
        }

        DbType dbType;
        try {
            dbType = DbType.valueOf(connectionInfo.getDatabaseType().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的数据源类型: " + connectionInfo.getDatabaseType(), ex);
        }

        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(dbType);
        if (processor == null) {
            throw new IllegalStateException(dbType + " 数据源插件未加载");
        }

        BaseDataSourceParamDTO parameters =
                DataSourceUtils.buildDatasourceParamDTO(dbType, connectionInfo.getConnectionParams());
        if (parameters == null || StringUtils.isBlank(parameters.getDatabase())) {
            throw new IllegalArgumentException("数据源未配置数据库，请先在数据源管理中配置数据库");
        }
        DataSourceUtils.checkDatasourceParam(parameters);

        DataSourceCatalog catalog = processor.getCatalog();
        if (catalog == null) {
            throw new IllegalStateException(dbType + " 数据源插件未提供 Catalog 元数据能力");
        }

        ConnectionParam connectionParam =
                DataSourceUtils.buildConnectionParams(dbType, connectionInfo.getConnectionParams());
        java.sql.Connection connection = processor.getConnection(connectionParam);
        if (connection == null) {
            throw new IllegalStateException(dbType + " 数据源连接创建失败");
        }
        return new DdlSession(dbType.name(), connection, catalog, parameters.getDatabase());
    }
}
