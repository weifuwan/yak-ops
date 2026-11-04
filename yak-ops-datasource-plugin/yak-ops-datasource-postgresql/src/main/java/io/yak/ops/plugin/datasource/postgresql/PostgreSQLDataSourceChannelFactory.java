package io.yak.ops.plugin.datasource.postgresql;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class PostgreSQLDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public String getName() {
        return DbType.POSTGRESQL.getName();
    }

    @Override
    public DataSourceChannel create() {
        return new PostgreSQLDataSourceChannel();
    }
}
