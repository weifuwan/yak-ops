package io.yak.ops.plugin.task.api;

import io.yak.ops.spi.plugin.PrioritySPI;
import io.yak.ops.spi.plugin.SPIIdentify;

public interface TaskChannelFactory extends PrioritySPI {

    default SPIIdentify getIdentify() {
        return SPIIdentify.builder()
                .name(getName())
                .build();
    }

    String getName();

    TaskChannel create();

}
