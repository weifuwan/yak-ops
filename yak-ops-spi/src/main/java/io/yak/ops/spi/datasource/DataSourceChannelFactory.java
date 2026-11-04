package io.yak.ops.spi.datasource;

import io.yak.ops.spi.plugin.PrioritySPI;
import io.yak.ops.spi.plugin.SPIIdentify;

public interface DataSourceChannelFactory extends PrioritySPI {

    /**
     * get datasource client
     */
    DataSourceChannel create();

    /**
     * get registry component name
     */
    String getName();

    @Override
    default SPIIdentify getIdentify() {
        return SPIIdentify.builder().name(getName()).build();
    }
}
