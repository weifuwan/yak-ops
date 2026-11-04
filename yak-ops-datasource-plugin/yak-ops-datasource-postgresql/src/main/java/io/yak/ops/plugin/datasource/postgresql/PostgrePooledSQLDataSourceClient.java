package io.yak.ops.plugin.datasource.postgresql;

import io.yak.ops.plugin.datasource.api.client.BasePooledDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class PostgrePooledSQLDataSourceClient extends BasePooledDataSourceClient {

    public PostgrePooledSQLDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }

}
