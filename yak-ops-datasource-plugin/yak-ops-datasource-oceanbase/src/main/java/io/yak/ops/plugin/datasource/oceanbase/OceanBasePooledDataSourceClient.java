package io.yak.ops.plugin.datasource.oceanbase;

import io.yak.ops.plugin.datasource.api.client.BasePooledDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class OceanBasePooledDataSourceClient extends BasePooledDataSourceClient {

    public OceanBasePooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
