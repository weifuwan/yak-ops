package io.yak.ops.server.master.registry;

import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.registry.api.ConnectionListener;
import io.yak.ops.registry.api.ConnectionState;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.registry.api.enums.RegistryNodeType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MasterConnectionStateListener implements ConnectionListener {

    private final RegistryClient registryClient;

    public MasterConnectionStateListener(final RegistryClient registryClient) {
        this.registryClient = registryClient;
    }

    @Override
    public void onUpdate(ConnectionState state) {
        log.info("Master received a {} event from registry, the current server state is {}", state,
                ServerLifeCycleManager.getServerStatus());
        switch (state) {
            case CONNECTED:
                break;
            case SUSPENDED:
                break;
            case RECONNECTED:
                log.warn("Master reconnect to registry");
                break;
            case DISCONNECTED:
                if (ServerLifeCycleManager.isStopped()) {
                    break;
                }
                registryClient.getStoppable(RegistryNodeType.MASTER).stop("Master disconnected from registry, will stop myself");
                break;
            default:
                log.warn("Unknown connection state: {}", state);
        }
    }
}
