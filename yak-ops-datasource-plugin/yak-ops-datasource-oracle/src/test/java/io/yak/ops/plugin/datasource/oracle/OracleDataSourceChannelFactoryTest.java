package io.yak.ops.plugin.datasource.oracle;

import io.yak.ops.spi.datasource.DataSourceChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OracleDataSourceChannelFactoryTest {

    @Test
    public void testCreate() {
        OracleDataSourceChannelFactory sourceChannelFactory = new OracleDataSourceChannelFactory();
        DataSourceChannel dataSourceChannel = sourceChannelFactory.create();
        Assertions.assertNotNull(dataSourceChannel);
    }
}
