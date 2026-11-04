package io.yak.ops.server.master.cluster;

import io.yak.ops.common.enums.ServerStatus;
import io.yak.ops.common.model.WorkerHeartBeat;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.dao.utils.WorkerGroupUtils;

import org.apache.commons.collections4.list.UnmodifiableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class WorkerClusters extends AbstractClusterSubscribeListener<WorkerServerMetadata>
        implements
            IClusters<WorkerServerMetadata>,
            WorkerGroupChangeNotifier.WorkerGroupListener {

    // WorkerIdentifier(workerAddress) -> worker
    private final Map<String, WorkerServerMetadata> workerMapping = new ConcurrentHashMap<>();

    // WorkerGroup from db -> WorkerIdentifier(workerAddress)
    private final Map<String, List<String>> dbWorkerGroupMapping = new ConcurrentHashMap<>();

    // WorkerGroup from config -> WorkerIdentifier(workerAddress)
    private final Map<String, List<String>> configWorkerGroupMapping = new ConcurrentHashMap<>();

    private final List<IClustersChangeListener<WorkerServerMetadata>> workerClusterChangeListeners =
            new CopyOnWriteArrayList<>();

    @Override
    public List<WorkerServerMetadata> getServers() {
        return UnmodifiableList.unmodifiableList(new ArrayList<>(workerMapping.values()));
    }

    @Override
    public Optional<WorkerServerMetadata> getServer(final String address) {
        return Optional.ofNullable(workerMapping.get(address));
    }

    public List<String> getDbWorkerServerAddressByGroup(String workerGroup) {
        if (WorkerGroupUtils.getDefaultWorkerGroup().equals(workerGroup)) {
            return UnmodifiableList.unmodifiableList(new ArrayList<>(workerMapping.keySet()));
        }
        return dbWorkerGroupMapping.getOrDefault(workerGroup, Collections.emptyList());
    }

    public List<String> getConfigWorkerServerAddressByGroup(String workerGroup) {
        if (WorkerGroupUtils.getDefaultWorkerGroup().equals(workerGroup)) {
            return UnmodifiableList.unmodifiableList(new ArrayList<>(workerMapping.keySet()));
        }
        return configWorkerGroupMapping.getOrDefault(workerGroup, Collections.emptyList());
    }

    public List<String> getNormalWorkerServerAddressByGroup(String workerGroup) {
        List<String> dbWorkerAddresses = getDbWorkerServerAddressByGroup(workerGroup)
                .stream()
                .map(workerMapping::get)
                .filter(Objects::nonNull)
                .filter(workerServer -> workerServer.getServerStatus() == ServerStatus.NORMAL)
                .map(WorkerServerMetadata::getAddress)
                .collect(Collectors.toList());
        List<String> configWorkerAddresses = getConfigWorkerServerAddressByGroup(workerGroup)
                .stream()
                .map(workerMapping::get)
                .filter(Objects::nonNull)
                .filter(workerServer -> workerServer.getServerStatus() == ServerStatus.NORMAL)
                .map(WorkerServerMetadata::getAddress)
                .collect(Collectors.toList());
        dbWorkerAddresses.removeAll(configWorkerAddresses);
        dbWorkerAddresses.addAll(configWorkerAddresses);
        return UnmodifiableList.unmodifiableList(dbWorkerAddresses);
    }

    public boolean containsWorkerGroup(String workerGroup) {
        return WorkerGroupUtils.getDefaultWorkerGroup().equals(workerGroup)
                || dbWorkerGroupMapping.containsKey(workerGroup)
                || configWorkerGroupMapping.containsKey(workerGroup);
    }

    @Override
    public void registerListener(IClustersChangeListener<WorkerServerMetadata> listener) {
        workerClusterChangeListeners.add(listener);
    }

    @Override
    public void onWorkerGroupDelete(List<WorkerGroup> workerGroups) {
        synchronized (dbWorkerGroupMapping) {
            for (WorkerGroup workerGroup : workerGroups) {
                dbWorkerGroupMapping.remove(workerGroup.getName());
            }
        }
    }

    @Override
    public void onWorkerGroupAdd(List<WorkerGroup> workerGroups) {
        // The logic of adding WorkerGroup is the same as updating WorkerGroup
        // Both need to change the WorkerGroup mapping to the latest
        onWorkerGroupChange(workerGroups);
    }

    @Override
    public void onWorkerGroupChange(List<WorkerGroup> workerGroups) {
        for (WorkerGroup workerGroup : workerGroups) {
            List<String> workerAddresses = WorkerGroupUtils.getWorkerAddressListFromWorkerGroup(workerGroup);
            synchronized (dbWorkerGroupMapping) {
                dbWorkerGroupMapping.put(workerGroup.getName(), workerAddresses);
            }
        }
    }

    @Override
    WorkerServerMetadata parseServerFromHeartbeat(String serverHeartBeatJson) {
        WorkerHeartBeat workerHeartBeat = JSONUtils.parseObject(serverHeartBeatJson, WorkerHeartBeat.class);
        if (workerHeartBeat == null) {
            return null;
        }
        return WorkerServerMetadata.parseFromHeartBeat(workerHeartBeat);
    }

    @Override
    public void onServerAdded(WorkerServerMetadata workerServer) {
        workerMapping.put(workerServer.getAddress(), workerServer);
        synchronized (configWorkerGroupMapping) {
            List<String> addWorkerGroupAddrList = configWorkerGroupMapping.get(workerServer.getWorkerGroup());
            if (addWorkerGroupAddrList == null) {
                List<String> newWorkerGroupAddrList = new ArrayList<>();
                newWorkerGroupAddrList.add(workerServer.getAddress());
                configWorkerGroupMapping.put(workerServer.getWorkerGroup(), newWorkerGroupAddrList);
            } else if (!addWorkerGroupAddrList.contains(workerServer.getAddress())) {
                addWorkerGroupAddrList.add(workerServer.getAddress());
                configWorkerGroupMapping.put(workerServer.getWorkerGroup(), addWorkerGroupAddrList);
            }
        }
        for (IClustersChangeListener<WorkerServerMetadata> listener : workerClusterChangeListeners) {
            listener.onServerAdded(workerServer);
        }
    }

    @Override
    public void onServerRemove(WorkerServerMetadata workerServer) {
        workerMapping.remove(workerServer.getAddress(), workerServer);
        synchronized (configWorkerGroupMapping) {
            List<String> removeWorkerGroupAddrList = configWorkerGroupMapping.get(workerServer.getWorkerGroup());
            if (removeWorkerGroupAddrList != null && removeWorkerGroupAddrList.contains(workerServer.getAddress())) {
                removeWorkerGroupAddrList.remove(workerServer.getAddress());
                if (removeWorkerGroupAddrList.isEmpty()) {
                    configWorkerGroupMapping.remove(workerServer.getWorkerGroup());
                }
            }
        }
        for (IClustersChangeListener<WorkerServerMetadata> listener : workerClusterChangeListeners) {
            listener.onServerRemove(workerServer);
        }
    }

    @Override
    public void onServerUpdate(WorkerServerMetadata workerServer) {
        workerMapping.put(workerServer.getAddress(), workerServer);
        for (IClustersChangeListener<WorkerServerMetadata> listener : workerClusterChangeListeners) {
            listener.onServerUpdate(workerServer);
        }
    }
}
