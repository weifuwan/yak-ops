package io.yak.ops.plugin.datasource.api.datasource;

import io.yak.ops.spi.datasource.BaseConnectionParam;

/**
 * Used for test.
 */
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
