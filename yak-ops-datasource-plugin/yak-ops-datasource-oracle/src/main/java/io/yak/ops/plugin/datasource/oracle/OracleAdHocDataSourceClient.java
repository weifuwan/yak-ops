package io.yak.ops.plugin.datasource.oracle;

import io.yak.ops.plugin.datasource.api.client.BaseAdHocDataSourceClient;
import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbType;

public class OracleAdHocDataSourceClient extends BaseAdHocDataSourceClient {

    public OracleAdHocDataSourceClient(BaseConnectionParam baseConnectionParam, DbType dbType) {
        super(baseConnectionParam, dbType);
    }
}
