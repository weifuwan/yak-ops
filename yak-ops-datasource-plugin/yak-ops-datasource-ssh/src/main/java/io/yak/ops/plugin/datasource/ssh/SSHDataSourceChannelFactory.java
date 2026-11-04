package io.yak.ops.plugin.datasource.ssh;

import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;

import com.google.auto.service.AutoService;

@AutoService(DataSourceChannelFactory.class)
public class SSHDataSourceChannelFactory implements DataSourceChannelFactory {

    @Override
    public String getName() {
        return DbType.SSH.getName();
    }

    @Override
    public DataSourceChannel create() {
        return new SSHDataSourceChannel();
    }
}
