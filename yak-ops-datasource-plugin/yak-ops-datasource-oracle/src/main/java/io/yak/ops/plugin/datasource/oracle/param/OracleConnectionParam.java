package io.yak.ops.plugin.datasource.oracle.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;
import io.yak.ops.spi.enums.DbConnectType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OracleConnectionParam extends BaseConnectionParam {

    protected DbConnectType connectType;

    @Override
    public String toString() {
        return "OracleConnectionParam{"
                + "user='" + user + '\''
                + ", password='" + password + '\''
                + ", address='" + address + '\''
                + ", database='" + database + '\''
                + ", jdbcUrl='" + jdbcUrl + '\''
                + ", driverLocation='" + driverLocation + '\''
                + ", driverClassName='" + driverClassName + '\''
                + ", validationQuery='" + validationQuery + '\''
                + ", other='" + other + '\''
                + ", connectType=" + connectType
                + '}';
    }
}
