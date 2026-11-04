package io.yak.ops.plugin.datasource.starrocks;

import io.yak.ops.plugin.datasource.api.client.BasePooledDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class StarRocksPooledDataSourceClient extends BasePooledDataSourceClient {

    public StarRocksPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }

}
