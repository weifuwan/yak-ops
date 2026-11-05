package io.yak.ops.plugin.datasource.api.catalog;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;

import java.util.List;

/**
 * Datasource metadata operations implemented by each plugin.
 * Additional database and table metadata operations can be introduced when needed.
 */
public interface DataSourceCatalog {

    List<ConnectionProperty> getConnectionProperties();
}
