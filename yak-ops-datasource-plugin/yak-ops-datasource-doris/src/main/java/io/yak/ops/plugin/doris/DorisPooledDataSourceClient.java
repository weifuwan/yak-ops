package io.yak.ops.plugin.doris;

import io.yak.ops.plugin.datasource.api.client.BasePooledDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class DorisPooledDataSourceClient extends BasePooledDataSourceClient {

    public DorisPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
