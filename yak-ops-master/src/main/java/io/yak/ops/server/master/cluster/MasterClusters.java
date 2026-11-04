package io.yak.ops.server.master.cluster;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.model.MasterHeartBeat;
import io.yak.ops.common.utils.JSONUtils;

import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MasterClusters extends AbstractClusterSubscribeListener<MasterServerMetadata>
        implements
            IClusters<MasterServerMetadata> {

    /**
     * Master address -> MasterServer
     **/
    private final Map<String, MasterServerMetadata> masterServerMap = new ConcurrentHashMap<>();

    private final List<IClustersChangeListener<MasterServerMetadata>> masterClusterChangeListeners =
            new CopyOnWriteArrayList<>();

    @Override
    public List<MasterServerMetadata> getServers() {
        return UnmodifiableList.unmodifiableList(new ArrayList<>(masterServerMap.values()));
    }

    @Override
    public Optional<MasterServerMetadata> getServer(final String address) {
        return Optional.ofNullable(masterServerMap.get(address));
    }

    public List<MasterServerMetadata> getNormalServers() {
        List<MasterServerMetadata> normalMasterServers = masterServerMap.values()
                .stream()
                .filter(masterServer -> masterServer.getServerStatus() == ServerStatus.NORMAL)
                .collect(Collectors.toList());
        return UnmodifiableList.unmodifiableList(normalMasterServers);
    }

    @Override
    public void registerListener(final IClustersChangeListener<MasterServerMetadata> listener) {
        masterClusterChangeListeners.add(listener);
    }

    @Override
    MasterServerMetadata parseServerFromHeartbeat(final String masterHeartBeatJson) {
        MasterHeartBeat masterHeartBeat = JSONUtils.parseObject(masterHeartBeatJson, MasterHeartBeat.class);
        if (masterHeartBeat == null) {
            return null;
        }
        return MasterServerMetadata.parseFromHeartBeat(masterHeartBeat);
    }

    @Override
    public void onServerAdded(final MasterServerMetadata masterServer) {
        masterServerMap.put(masterServer.getAddress(), masterServer);
        for (IClustersChangeListener<MasterServerMetadata> listener : masterClusterChangeListeners) {
            listener.onServerAdded(masterServer);
        }
    }

    @Override
    public void onServerRemove(final MasterServerMetadata masterServer) {
        masterServerMap.remove(masterServer.getAddress());
        for (IClustersChangeListener<MasterServerMetadata> listener : masterClusterChangeListeners) {
            listener.onServerRemove(masterServer);
        }
    }

    @Override
    public void onServerUpdate(final MasterServerMetadata masterServer) {
        masterServerMap.put(masterServer.getAddress(), masterServer);
        for (IClustersChangeListener<MasterServerMetadata> listener : masterClusterChangeListeners) {
            listener.onServerUpdate(masterServer);
        }
    }

}
