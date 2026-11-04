package io.yak.ops.plugin.registry.zookeeper;

import io.yak.ops.registry.api.ConnectionListener;
import io.yak.ops.registry.api.ConnectionState;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionStateListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ZookeeperConnectionStateListener implements ConnectionStateListener {

    private final ConnectionListener listener;

    ZookeeperConnectionStateListener(ConnectionListener listener) {
        this.listener = listener;
    }

    @Override
    public void stateChanged(CuratorFramework client,
                             org.apache.curator.framework.state.ConnectionState newState) {
        switch (newState) {
            case CONNECTED:
                log.info("Registry connected");
                listener.onUpdate(ConnectionState.CONNECTED);
                break;
            case LOST:
                log.warn("Registry disconnected");
                listener.onUpdate(ConnectionState.DISCONNECTED);
                break;
            case RECONNECTED:
                log.info("Registry reconnected");
                listener.onUpdate(ConnectionState.RECONNECTED);
                break;
            case SUSPENDED:
                log.warn("Registry suspended");
                listener.onUpdate(ConnectionState.SUSPENDED);
                break;
            default:
                break;
        }
    }
}
