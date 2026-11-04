package io.yak.ops.plugin.datasource.db2;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DB2DataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        DB2DataSourceChannelFactory sourceChannelFactory = new DB2DataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
