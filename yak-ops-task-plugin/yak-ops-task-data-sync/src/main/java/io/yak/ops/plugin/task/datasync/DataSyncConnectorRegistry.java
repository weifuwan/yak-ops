package io.yak.ops.plugin.task.datasync;

import io.yak.ops.spi.enums.DbType;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/** Resolves independent source and sink capabilities through the installed SPI providers. */
public final class DataSyncConnectorRegistry {

    private final Map<String, DataSyncSourceProvider> sources = new HashMap<>();
    private final Map<String, DataSyncSinkProvider> sinks = new HashMap<>();

    public DataSyncConnectorRegistry() {
        ServiceLoader.load(DataSyncSourceProvider.class).forEach(provider -> {
            String key = key(provider.getDbType(), provider.getConnectorType());
            if (sources.putIfAbsent(key, provider) != null) {
                throw new IllegalStateException("Duplicate DATA_SYNC source provider: " + key);
            }
        });
        ServiceLoader.load(DataSyncSinkProvider.class).forEach(provider -> {
            String key = key(provider.getDbType(), provider.getConnectorType());
            if (sinks.putIfAbsent(key, provider) != null) {
                throw new IllegalStateException("Duplicate DATA_SYNC sink provider: " + key);
            }
        });
    }

    public DataSyncSourceProvider source(DbType type, String connector) {
        DataSyncSourceProvider provider = sources.get(key(type, connector));
        if (provider == null) {
            throw new IllegalArgumentException("No DATA_SYNC source provider for " + key(type, connector));
        }
        return provider;
    }

    public DataSyncSinkProvider sink(DbType type, String connector) {
        DataSyncSinkProvider provider = sinks.get(key(type, connector));
        if (provider == null) {
            throw new IllegalArgumentException("No DATA_SYNC sink provider for " + key(type, connector));
        }
        return provider;
    }

    private static String key(DbType type, String connector) {
        return type.name() + ":" + connector;
    }
}
