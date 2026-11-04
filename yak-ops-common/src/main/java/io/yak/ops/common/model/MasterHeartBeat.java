package io.yak.ops.common.model;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
public class MasterHeartBeat extends BaseHeartBeat implements HeartBeat {

    private boolean isCoordinator;

}
