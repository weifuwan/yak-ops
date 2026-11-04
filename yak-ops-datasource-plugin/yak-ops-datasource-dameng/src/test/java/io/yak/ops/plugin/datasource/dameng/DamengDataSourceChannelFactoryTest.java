package io.yak.ops.plugin.datasource.dameng;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DamengDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        DamengDataSourceChannelFactory sourceChannelFactory = new DamengDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
