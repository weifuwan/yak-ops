package io.yak.ops.plugin.datasource.oracle;

import io.yak.ops.plugin.datasource.api.client.BasePooledDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class OraclePooledDataSourceClient extends BasePooledDataSourceClient {

    public OraclePooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }

}
