package io.yak.ops.plugin.datasource.clickhouse;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class ClickHouseDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public String getName() {
        return DbType.CLICKHOUSE.getName();
    }

    @Override
    public DataSourceChannel create() {
        return new ClickHouseDataSourceChannel();
    }
}
