package io.yak.ops.server.master.cluster;

import java.util.List;

public class MasterSlotChangeListenerAdaptor
        implements
            IMasterSlotChangeListener,
            IClusters.IClustersChangeListener<MasterServerMetadata> {

    private final MasterSlotManager masterSlotManager;

    private final MasterClusters masterClusters;

    public MasterSlotChangeListenerAdaptor(final MasterSlotManager masterSlotManager,
                                           final MasterClusters masterClusters) {
        this.masterSlotManager = masterSlotManager;
        this.masterClusters = masterClusters;
    }

    @Override
    public void onMasterSlotChanged(final List<MasterServerMetadata> normalMasterServers) {
        masterSlotManager.doReBalance(normalMasterServers);
    }

    @Override
    public void onServerAdded(MasterServerMetadata server) {
        onMasterSlotChanged(masterClusters.getNormalServers());
    }

    @Override
    public void onServerRemove(MasterServerMetadata server) {
        onMasterSlotChanged(masterClusters.getNormalServers());
    }

    @Override
    public void onServerUpdate(MasterServerMetadata server) {
        onMasterSlotChanged(masterClusters.getNormalServers());
    }
}
