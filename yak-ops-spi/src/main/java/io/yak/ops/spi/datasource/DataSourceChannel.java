package io.yak.ops.spi.datasource;

import io.yak.ops.spi.enums.DbType;

public interface DataSourceChannel {

    /**
     * Create a AdHocDataSourceClient, this client should not be pooled.
     */
    AdHocDataSourceClient createAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType);

    /**
     * Create a PooledDataSourceClient, this client should be pooled.
     */
    PooledDataSourceClient createPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType);
}
