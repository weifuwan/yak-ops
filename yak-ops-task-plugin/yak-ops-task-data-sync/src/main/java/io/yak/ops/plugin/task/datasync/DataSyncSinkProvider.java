package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.spi.enums.DbType;

import java.util.Map;

public interface DataSyncSinkProvider {

    DbType getDbType();

    String getConnectorType();

    Map<String, String> connectionOptions(DataSourceParameters sink);
}
