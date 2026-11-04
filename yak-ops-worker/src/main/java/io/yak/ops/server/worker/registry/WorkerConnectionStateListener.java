package io.yak.ops.server.worker.registry;

import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.registry.api.ConnectionListener;
import io.yak.ops.registry.api.ConnectionState;
import io.yak.ops.registry.api.RegistryClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkerConnectionStateListener implements ConnectionListener {

    private final RegistryClient registryClient;

    public WorkerConnectionStateListener(final RegistryClient registryClient) {
        this.registryClient = registryClient;
    }

    @Override
    public void onUpdate(ConnectionState state) {
        log.info("Worker received a {} event from registry, the current server state is {}", state,
                ServerLifeCycleManager.getServerStatus());
        switch (state) {
            case CONNECTED:
                break;
            case SUSPENDED:
                break;
            case RECONNECTED:
                log.warn("Worker reconnect to registry");
                break;
            case DISCONNECTED:
                registryClient.getStoppable().stop("Worker disconnected from registry, will stop myself");
            default:
        }
    }
}
