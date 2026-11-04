package io.yak.ops.plugin.datasource.clickhouse;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClickHouseDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        ClickHouseDataSourceChannelFactory sourceChannelFactory = new ClickHouseDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
