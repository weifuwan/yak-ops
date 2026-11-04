package io.yak.ops.plugin.datasource.clickhouse.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class ClickHouseConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "ClickHouseConnectionParam{"
                + "user='" + user + '\''
                + ", password='" + password + '\''
                + ", address='" + address + '\''
                + ", database='" + database + '\''
                + ", jdbcUrl='" + jdbcUrl + '\''
                + ", driverLocation='" + driverLocation + '\''
                + ", driverClassName='" + driverClassName + '\''
                + ", validationQuery='" + validationQuery + '\''
                + ", other='" + other + '\''
                + '}';
    }
}
