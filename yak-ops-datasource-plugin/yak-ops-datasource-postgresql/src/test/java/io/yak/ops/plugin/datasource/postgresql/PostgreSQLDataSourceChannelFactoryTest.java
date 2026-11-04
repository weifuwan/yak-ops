package io.yak.ops.plugin.datasource.postgresql;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PostgreSQLDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        PostgreSQLDataSourceChannelFactory sourceChannelFactory = new PostgreSQLDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
