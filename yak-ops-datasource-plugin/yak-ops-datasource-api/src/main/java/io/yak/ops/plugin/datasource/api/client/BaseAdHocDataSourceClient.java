package io.yak.ops.plugin.datasource.api.client;

import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginManager;
import io.yak.ops.spi.datasource.AdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

import java.sql.Connection;
import java.sql.SQLException;

public abstract class BaseAdHocDataSourceClient implements AdHocDataSourceClient {

    private final BaseConnectionParam baseConnectionParam;
    private final DbType dbType;

    protected BaseAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        this.baseConnectionParam = baseConnectionParam;
        this.dbType = dbType;
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return DataSourcePluginManager.getDataSourceProcessor(dbType).getConnection(baseConnectionParam);
        } catch (Exception e) {
            throw new SQLException("Create adhoc connection error", e);
        }
    }

    @Override
    public void close() {
        // do nothing
    }
}
