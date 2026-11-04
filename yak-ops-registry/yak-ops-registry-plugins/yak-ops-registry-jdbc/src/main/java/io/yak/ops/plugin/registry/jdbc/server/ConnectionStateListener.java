package io.yak.ops.plugin.registry.jdbc.server;

public interface ConnectionStateListener {

    void onConnected();

    void onDisConnected();

    void onReconnected();
}
