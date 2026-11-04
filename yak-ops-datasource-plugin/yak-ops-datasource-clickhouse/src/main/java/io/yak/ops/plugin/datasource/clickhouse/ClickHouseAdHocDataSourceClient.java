package io.yak.ops.plugin.datasource.clickhouse;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class ClickHouseAdHocDataSourceClient extends BaseAdHocDataSourceClient {

    public ClickHouseAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
