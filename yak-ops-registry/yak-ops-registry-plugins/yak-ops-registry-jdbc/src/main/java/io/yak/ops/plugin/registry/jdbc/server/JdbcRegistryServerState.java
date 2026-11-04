package io.yak.ops.plugin.registry.jdbc.server;

import io.yak.ops.plugin.registry.jdbc.JdbcRegistryProperties;

public enum JdbcRegistryServerState {
    /**
     * Once the {@link JdbcRegistryServer} created, it will be in the INIT state.
     */
    INIT,
    /**
     * After the {@link JdbcRegistryServer} started, it will be in the STARTED state.
     */
    STARTED,
    /**
     * Once the {@link JdbcRegistryServer} cannot connect to DB, it will be in the SUSPENDED state, and if it can reconnect to DB in {@link JdbcRegistryProperties#getSessionTimeout()} ()},
     * it will be changed to the STARTED state again.
     */
    SUSPENDED,
    /**
     * If the {@link JdbcRegistryServer} cannot connected to DB in {@link JdbcRegistryProperties#getSessionTimeout()}, it will be in the DISCONNECTED state.
     */
    DISCONNECTED,
    /**
     * If the {@link JdbcRegistryServer} closed, it will be in the STOPPED state.
     */
    STOPPED,
    ;

}
