package io.yak.ops.common.model;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
public class AlertServerHeartBeat extends BaseHeartBeat implements HeartBeat {

    /**
     * If the alert server is active or standby
     */
    private boolean isActive;

}
