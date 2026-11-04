package io.yak.ops.spi.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class SPIIdentify {

    private static final int DEFAULT_PRIORITY = 0;

    private String name;

    @Builder.Default
    private int priority = DEFAULT_PRIORITY;

}
