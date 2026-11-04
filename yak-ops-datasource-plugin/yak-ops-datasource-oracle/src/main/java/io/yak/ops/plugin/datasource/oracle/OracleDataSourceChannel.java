package io.yak.ops.plugin.datasource.oracle;

import io.yak.ops.spi.datasource.AdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.PooledDataSourceClient;
import io.yak.ops.spi.enums.DbType;

public class OracleDataSourceChannel implements DataSourceChannel {

    @Override
    public AdHocDataSourceClient createAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        return new OracleAdHocDataSourceClient(baseConnectionParam, dbType);
    }

    @Override
    public PooledDataSourceClient createPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        return new OraclePooledDataSourceClient(baseConnectionParam, dbType);
    }
}
