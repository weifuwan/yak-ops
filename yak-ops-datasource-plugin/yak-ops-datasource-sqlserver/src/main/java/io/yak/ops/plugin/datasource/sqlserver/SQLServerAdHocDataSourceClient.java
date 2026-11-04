package io.yak.ops.plugin.datasource.sqlserver;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class SQLServerAdHocDataSourceClient extends BaseAdHocDataSourceClient {

    public SQLServerAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
