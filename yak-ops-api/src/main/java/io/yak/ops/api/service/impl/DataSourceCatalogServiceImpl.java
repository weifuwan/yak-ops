package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.DataSourceCatalogService;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.plugin.datasource.api.datasource.DataSourceProcessor;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.enums.DbType;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Delegates metadata discovery to the registered datasource plugin.
 */
@Service
public class DataSourceCatalogServiceImpl implements DataSourceCatalogService {

    @Override
    public List<ConnectionProperty> getConnectionProperties(DbType dbType) {
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(dbType);
        if (processor == null) {
            throw new IllegalArgumentException("No registered datasource processor for " + dbType);
        }
        return processor.getConnectionProperties();
    }
}
