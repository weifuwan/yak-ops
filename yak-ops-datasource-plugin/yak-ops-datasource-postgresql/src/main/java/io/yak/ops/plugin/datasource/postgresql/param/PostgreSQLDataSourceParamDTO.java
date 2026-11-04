package io.yak.ops.plugin.datasource.postgresql.param;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.spi.enums.DbType;

public class PostgreSQLDataSourceParamDTO extends BaseDataSourceParamDTO {

    @Override
    public String toString() {
        return "PostgreSQLDataSourceParamDTO{"
                + "host='" + host + '\''
                + ", port=" + port
                + ", database='" + database + '\''
                + ", userName='" + userName + '\''
                + ", password='" + password + '\''
                + ", other='" + other + '\''
                + '}';
    }

    @Override
    public DbType getType() {
        return DbType.POSTGRESQL;
    }
}
