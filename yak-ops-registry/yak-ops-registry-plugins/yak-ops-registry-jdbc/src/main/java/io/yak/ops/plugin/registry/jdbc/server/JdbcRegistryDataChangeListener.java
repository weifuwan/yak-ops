package io.yak.ops.plugin.registry.jdbc.server;

public interface JdbcRegistryDataChangeListener {

    void onJdbcRegistryDataChanged(String key, String value);

    void onJdbcRegistryDataDeleted(String key, String value);

    void onJdbcRegistryDataAdded(String key, String value);

}
