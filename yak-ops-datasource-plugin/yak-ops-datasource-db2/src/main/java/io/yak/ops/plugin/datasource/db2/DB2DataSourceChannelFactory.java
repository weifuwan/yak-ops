package io.yak.ops.plugin.datasource.db2;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class DB2DataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public String getName() {
        return DbType.DB2.getName();
    }

    @Override
    public DataSourceChannel create() {
        return new DB2DataSourceChannel();
    }
}
