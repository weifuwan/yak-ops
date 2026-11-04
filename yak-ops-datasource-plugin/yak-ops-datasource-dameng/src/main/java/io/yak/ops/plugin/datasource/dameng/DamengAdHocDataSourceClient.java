package io.yak.ops.plugin.datasource.dameng;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class DamengAdHocDataSourceClient extends BaseAdHocDataSourceClient {

    public DamengAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
