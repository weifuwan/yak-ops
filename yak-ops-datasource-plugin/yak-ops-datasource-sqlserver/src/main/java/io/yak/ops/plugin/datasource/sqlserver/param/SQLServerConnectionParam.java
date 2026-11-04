package io.yak.ops.plugin.datasource.sqlserver.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class SQLServerConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "SQLServerConnectionParam{"
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
