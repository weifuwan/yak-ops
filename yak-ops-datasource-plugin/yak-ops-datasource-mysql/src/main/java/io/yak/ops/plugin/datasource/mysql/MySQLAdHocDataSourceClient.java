package io.yak.ops.plugin.datasource.mysql;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class MySQLAdHocDataSourceClient extends BaseAdHocDataSourceClient {

    public MySQLAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
