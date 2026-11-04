package io.yak.ops.plugin.datasource.starrocks;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class StarRocksDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public DataSourceChannel create() {
        return new StarRocksDataSourceChannel();
    }

    @Override
    public String getName() {
        return DbType.STARROCKS.getName();
    }
}
