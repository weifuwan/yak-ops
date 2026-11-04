package io.yak.ops.server.master.cluster;

import java.util.List;

public interface IMasterSlotChangeListener {

    void onMasterSlotChanged(final List<MasterServerMetadata> normalMasterServers);
}
