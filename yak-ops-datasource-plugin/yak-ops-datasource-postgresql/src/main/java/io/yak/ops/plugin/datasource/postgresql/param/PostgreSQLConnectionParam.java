package io.yak.ops.plugin.datasource.postgresql.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class PostgreSQLConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "PostgreSQLConnectionParam{"
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
