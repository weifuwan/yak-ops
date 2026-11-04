package io.yak.ops.plugin.doris;

import io.yak.ops.spi.datasource.AdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.PooledDataSourceClient;
import io.yak.ops.spi.enums.DbType;

public class DorisDataSourceChannel implements DataSourceChannel {

    @Override
    public AdHocDataSourceClient createAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        return new DorisAdHocDataSourceClient(baseConnectionParam, dbType);
    }

    @Override
    public PooledDataSourceClient createPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        return new DorisPooledDataSourceClient(baseConnectionParam, dbType);
    }
}
