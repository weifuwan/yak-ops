package io.yak.ops.common.model;

import io.yak.ops.common.enums.ServerStatus;

public interface HeartBeat {

    ServerStatus getServerStatus();

}
