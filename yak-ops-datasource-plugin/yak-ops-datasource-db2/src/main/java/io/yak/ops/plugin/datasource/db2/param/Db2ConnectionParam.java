package io.yak.ops.plugin.datasource.db2.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class Db2ConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "Db2ConnectionParam{"
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
