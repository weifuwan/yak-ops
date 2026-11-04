package io.yak.ops.plugin.datasource.dameng.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class DamengConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "DamengConnectionParam{"
                + "user='" + user + '\''
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
