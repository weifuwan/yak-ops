package io.yak.ops.registry.api;

/**
 * Connection state between client and registry center(Etcd, MySql, Zookeeper)
 */
public enum ConnectionState {
    CONNECTED,
    RECONNECTED,
    SUSPENDED,
    DISCONNECTED
}
