package io.yak.ops.spi.datasource;

import io.yak.ops.spi.enums.DbType;

import javax.sql.DataSource;

/**
 * This is a marker interface for pooled data source client, which means the connection is pooled.
 */
public interface PooledDataSourceClient extends DataSourceClient {

    DataSource createDataSourcePool(BaseConnectionParam baseConnectionParam, DbType dbType);

}
