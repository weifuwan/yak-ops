package io.yak.ops.plugin.datasource.dameng;

import io.yak.ops.plugin.datasource.api.client.BasePooledDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class DamengPooledDataSourceClient extends BasePooledDataSourceClient {

    public DamengPooledDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }

}
