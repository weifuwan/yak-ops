package io.yak.ops.plugin.datasource.mysql;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MySQLDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        MySQLDataSourceChannelFactory sourceChannelFactory = new MySQLDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
