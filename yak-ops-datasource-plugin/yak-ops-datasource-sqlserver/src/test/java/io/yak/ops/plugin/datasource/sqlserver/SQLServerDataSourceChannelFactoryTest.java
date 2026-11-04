package io.yak.ops.plugin.datasource.sqlserver;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SQLServerDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        SQLServerDataSourceChannelFactory sourceChannelFactory = new SQLServerDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
