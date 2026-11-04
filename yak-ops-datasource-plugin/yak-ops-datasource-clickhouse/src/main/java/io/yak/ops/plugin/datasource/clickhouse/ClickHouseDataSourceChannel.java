package io.yak.ops.plugin.datasource.clickhouse;

import io.yak.ops.spi.datasource.AdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.PooledDataSourceClient;
import io.yak.ops.spi.enums.DbType;

public class ClickHouseDataSourceChannel implements DataSourceChannel {

    @Override
    public AdHocDataSourceClient createAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        return new ClickHouseAdHocDataSourceClient(baseConnectionParam, dbType);
    }

    @Override
    public PooledDataSourceClient createPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        return new ClickHousePooledDataSourceClient(baseConnectionParam, dbType);
    }
}
