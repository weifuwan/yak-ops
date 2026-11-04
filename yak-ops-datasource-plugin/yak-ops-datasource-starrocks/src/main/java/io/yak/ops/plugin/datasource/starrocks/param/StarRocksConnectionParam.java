package io.yak.ops.plugin.datasource.starrocks.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

public class StarRocksConnectionParam extends BaseConnectionParam {

    @Override
    public String toString() {
        return "StarRocksConnectionParam{" +
                "user='" + user + '\'' +
                ", address='" + address + '\'' +
                ", database='" + database + '\'' +
                ", jdbcUrl='" + jdbcUrl + '\'' +
                ", driverLocation='" + driverLocation + '\'' +
                ", driverClassName='" + driverClassName + '\'' +
                ", validationQuery='" + validationQuery + '\'' +
                ", other=" + other +
                '}';
    }
}
