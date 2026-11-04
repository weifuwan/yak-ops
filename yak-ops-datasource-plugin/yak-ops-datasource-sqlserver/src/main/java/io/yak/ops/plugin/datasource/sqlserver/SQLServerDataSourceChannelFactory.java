package io.yak.ops.plugin.datasource.sqlserver;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class SQLServerDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public String getName() {
        return DbType.SQLSERVER.getName();
    }

    @Override
    public DataSourceChannel create() {
        return new SQLServerDataSourceChannel();
    }
}
