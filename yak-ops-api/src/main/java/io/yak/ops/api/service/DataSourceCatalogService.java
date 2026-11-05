package io.yak.ops.api.service;

import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.spi.enums.DbType;

import java.util.List;

/**
 * Application service exposing datasource catalog metadata.
 */
public interface DataSourceCatalogService {

    List<ConnectionProperty> getConnectionProperties(DbType dbType);
}
