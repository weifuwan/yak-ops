package io.yak.ops.plugin.doris;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class DorisDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public DataSourceChannel create() {
        return new DorisDataSourceChannel();
    }

    @Override
    public String getName() {
        return DbType.DORIS.getName();
    }
}
