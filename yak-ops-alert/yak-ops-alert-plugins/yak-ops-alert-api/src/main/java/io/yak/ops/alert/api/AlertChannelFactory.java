package io.yak.ops.alert.api;

import io.yak.ops.spi.params.base.PluginParams;
import io.yak.ops.spi.plugin.PrioritySPI;
import io.yak.ops.spi.plugin.SPIIdentify;

import java.util.List;

public interface AlertChannelFactory extends PrioritySPI {

    /**
     * Returns the name of the alert channel
     *
     * @return the name of the alert channel
     */
    String name();

    /**
     * Create an alert channel
     *
     * @return alert channel
     */
    AlertChannel create();

    /**
     * Returns the configurable parameters that this plugin needs to display on the web ui
     */
    List<PluginParams> params();

    default SPIIdentify getIdentify() {
        return SPIIdentify.builder().name(name()).build();
    }
}
