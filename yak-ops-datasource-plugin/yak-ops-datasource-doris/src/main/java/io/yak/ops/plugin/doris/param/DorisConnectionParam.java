package io.yak.ops.plugin.doris.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class DorisConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "DorisConnectionParam{"
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
