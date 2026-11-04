package io.yak.ops.plugin.datasource.mysql;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class MySQLDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public String getName() {
        return DbType.MYSQL.getName();
    }

    @Override
    public DataSourceChannel create() {
        return new MySQLDataSourceChannel();
    }
}
