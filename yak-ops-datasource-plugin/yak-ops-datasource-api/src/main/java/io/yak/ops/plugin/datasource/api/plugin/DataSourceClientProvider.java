package io.yak.ops.plugin.datasource.api.plugin;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.datasource.AdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceClient;
import io.yak.ops.spi.datasource.PooledDataSourceClient;
import io.yak.ops.spi.enums.DbType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataSourceClientProvider {

    // We use the cache here to avoid creating a new datasource client every time,
    // One DataSourceClient corresponds to one unique datasource.
    private static final Cache<String, PooledDataSourceClient> POOLED_DATASOURCE_CLIENT_CACHE =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(PropertyUtils.getLong(DataSourceConstants.KERBEROS_EXPIRE_TIME, 24L),
                            TimeUnit.HOURS)
                    .removalListener((RemovalListener<String, PooledDataSourceClient>) notification -> {
                        try (PooledDataSourceClient closedClient = notification.getValue()) {
                            log.info("Datasource: {} is removed from cache due to expire", notification.getKey());
                        } catch (Exception e) {
                            log.error("Close datasource client error", e);
                        }
                    })
                    .maximumSize(100)
                    .build();

    public static DataSourceClient getPooledDataSourceClient(DbType dbType,
                                                             ConnectionParam connectionParam) throws ExecutionException {
        BaseConnectionParam baseConnectionParam = (BaseConnectionParam) connectionParam;
        String datasourceUniqueId = DataSourceUtils.getDatasourceUniqueId(baseConnectionParam, dbType);
        return POOLED_DATASOURCE_CLIENT_CACHE.get(datasourceUniqueId, () -> {
            DataSourceChannel dataSourceChannel = DataSourcePluginManager.getDataSourceChannel(dbType);
            if (null == dataSourceChannel) {
                throw new RuntimeException(String.format("datasource plugin '%s' is not found", dbType.getName()));
            }
            return dataSourceChannel.createPooledDataSourceClient(baseConnectionParam, dbType);
        });
    }

    public static Connection getPooledConnection(DbType dbType,
                                                 ConnectionParam connectionParam) throws SQLException, ExecutionException {
        return getPooledDataSourceClient(dbType, connectionParam).getConnection();
    }

    public static AdHocDataSourceClient getAdHocDataSourceClient(DbType dbType, ConnectionParam connectionParam) {
        BaseConnectionParam baseConnectionParam = (BaseConnectionParam) connectionParam;
        DataSourceChannel dataSourceChannel = DataSourcePluginManager.getDataSourceChannel(dbType);
        if (null == dataSourceChannel) {
            throw new RuntimeException(String.format("datasource plugin '%s' is not found", dbType.getName()));
        }
        return dataSourceChannel.createAdHocDataSourceClient(baseConnectionParam, dbType);
    }

    public static Connection getAdHocConnection(DbType dbType,
                                                ConnectionParam connectionParam) throws SQLException, ExecutionException {
        return getAdHocDataSourceClient(dbType, connectionParam).getConnection();
    }
}
