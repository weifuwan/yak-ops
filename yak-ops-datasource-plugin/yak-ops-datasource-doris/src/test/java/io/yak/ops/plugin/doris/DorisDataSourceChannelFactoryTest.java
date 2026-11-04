package io.yak.ops.plugin.doris;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DorisDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        DorisDataSourceChannelFactory sourceChannelFactory = new DorisDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
