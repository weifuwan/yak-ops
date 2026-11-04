package io.yak.ops.server.master.cluster;

import java.util.List;

public interface IMasterSlotReBalancer {

    void doReBalance(List<MasterServerMetadata> masterServerList);
}
