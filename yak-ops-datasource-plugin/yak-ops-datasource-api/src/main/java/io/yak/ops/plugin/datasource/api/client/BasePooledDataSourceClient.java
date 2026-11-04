package io.yak.ops.plugin.datasource.api.client;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.datasource.PooledDataSourceClient;
import io.yak.ops.spi.enums.DbType;

import org.apache.commons.collections4.MapUtils;

import java.sql.Connection;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BasePooledDataSourceClient implements PooledDataSourceClient {

    protected final BaseConnectionParam baseConnectionParam;
    protected HikariDataSource dataSource;

    public BasePooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {

        this.baseConnectionParam = checkNotNull(baseConnectionParam, "baseConnectionParam is null");
        this.dataSource = createDataSourcePool(baseConnectionParam, checkNotNull(dbType, "dbType is null"));
    }

    // todo: support multiple version databases
    @Override
    public HikariDataSource createDataSourcePool(BaseConnectionParam baseConnectionParam, DbType dbType) {

        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setDriverClassName(baseConnectionParam.getDriverClassName());
        dataSource.setJdbcUrl(DataSourceUtils.getJdbcUrl(dbType, baseConnectionParam));
        dataSource.setUsername(baseConnectionParam.getUser());
        dataSource.setPassword(PasswordUtils.decodePassword(baseConnectionParam.getPassword()));

        dataSource.setMinimumIdle(PropertyUtils.getInt(DataSourceConstants.SPRING_DATASOURCE_MIN_IDLE, 5));
        dataSource.setMaximumPoolSize(PropertyUtils.getInt(DataSourceConstants.SPRING_DATASOURCE_MAX_ACTIVE, 50));
        dataSource.setConnectionTestQuery(baseConnectionParam.getValidationQuery());

        if (MapUtils.isNotEmpty(baseConnectionParam.getOther())) {
            baseConnectionParam.getOther().forEach(dataSource::addDataSourceProperty);
        }

        log.info("Creating HikariDataSource for {} success.", dbType.name());
        return dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        log.info("do close dataSource {}.", baseConnectionParam.getDatabase());
        try (HikariDataSource closedDatasource = dataSource) {
            // only close the resource
        }
    }

}
