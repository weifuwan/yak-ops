package io.yak.ops.plugin.datasource.mysql.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class MySQLConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "MySQLConnectionParam{"
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
