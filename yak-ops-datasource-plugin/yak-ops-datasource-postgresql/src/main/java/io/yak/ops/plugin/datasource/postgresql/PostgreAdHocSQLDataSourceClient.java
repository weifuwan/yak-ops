package io.yak.ops.plugin.datasource.postgresql;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class PostgreAdHocSQLDataSourceClient extends BaseAdHocDataSourceClient {

    public PostgreAdHocSQLDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
