package io.yak.ops.plugin.datasource.api.plugin;

import static java.lang.String.format;

import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.spi.datasource.DataSourceChannel;
import io.yak.ops.spi.datasource.DataSourceChannelFactory;
import io.yak.ops.spi.enums.DbType;
import io.yak.ops.spi.plugin.PrioritySPIFactory;

import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataSourcePluginManager {

    private static final Map<String, DataSourceChannel> datasourceChannelMap = new ConcurrentHashMap<>();

    private static final Map<String, DataSourceProcessor> dataSourceProcessorMap = new ConcurrentHashMap<>();

    static {
        loadDataSourcePlugin();
    }

    public static DataSourceChannel getDataSourceChannel(@NonNull DbType dbType) {
        return datasourceChannelMap.get(dbType.getName());
    }

    public static DataSourceProcessor getDataSourceProcessor(@NonNull DbType dbType) {
        return dataSourceProcessorMap.get(dbType.getName());
    }

    public static void loadDataSourcePlugin() {
        initializeDataSourceChannel();
        initializeDataSourceProcessor();
    }

    private static synchronized void initializeDataSourceChannel() {
        if (MapUtils.isNotEmpty(datasourceChannelMap)) {
            return;
        }
        new PrioritySPIFactory<>(DataSourceChannelFactory.class).getSPIMap().forEach(
                (dataSourceChannelName, dataSourceChannelFactory) -> {
                    if (datasourceChannelMap.containsKey(dataSourceChannelName)) {
                        throw new IllegalStateException(
                                format("Duplicate datasource channel named '%s'", dataSourceChannelName));
                    }
                    datasourceChannelMap.put(dataSourceChannelName, dataSourceChannelFactory.create());
                    log.info("Registered datasource channel: {}", dataSourceChannelName);
                });
    }

    private static synchronized void initializeDataSourceProcessor() {
        if (MapUtils.isNotEmpty(dataSourceProcessorMap)) {
            return;
        }

        ServiceLoader.load(DataSourceProcessor.class).forEach(factory -> {
            final String name = factory.getDbType().getName();
            if (dataSourceProcessorMap.containsKey(name)) {
                throw new IllegalStateException(format("Duplicate datasource processor named '%s'", name));
            }
            DataSourceProcessor dataSourceProcessor = factory.create();
            dataSourceProcessorMap.put(name, dataSourceProcessor);
            log.info("Success register datasource processor -> {}", name);
        });
    }

}
