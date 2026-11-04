package io.yak.ops.spi.datasource;

/**
 * This is a marker interface for pooled data source client, the connection generated from this client should not be pooled.
 */
public interface AdHocDataSourceClient extends DataSourceClient {

}
