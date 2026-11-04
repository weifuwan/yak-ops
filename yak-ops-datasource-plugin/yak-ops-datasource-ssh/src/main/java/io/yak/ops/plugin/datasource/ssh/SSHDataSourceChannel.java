package io.yak.ops.plugin.datasource.ssh;

import io.yak.ops.spi.datasource.AdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.PooledDataSourceClient;
import io.yak.ops.spi.enums.DbType;

public class SSHDataSourceChannel implements DataSourceChannel {

    @Override
    public AdHocDataSourceClient createAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        throw new UnsupportedOperationException("SSH AdHocDataSourceClient is not supported");
    }

    @Override
    public PooledDataSourceClient createPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        throw new UnsupportedOperationException("SSH PooledDataSourceClient is not supported");
    }
}
