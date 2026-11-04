package io.yak.ops.plugin.datasource.oceanbase;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class OceanBaseAdHocDataSourceClient extends BaseAdHocDataSourceClient {

    public OceanBaseAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
