package io.yak.ops.plugin.datasource.oracle.param;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.spi.enums.DbConnectType;
import io.yak.ops.spi.enums.DbType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OracleDataSourceParamDTO extends BaseDataSourceParamDTO {

    private DbConnectType connectType;

    @Override
    public String toString() {
        return "OracleDataSourceParamDTO{"
                + "name='" + name + '\''
                + ", note='" + note + '\''
                + ", host='" + host + '\''
                + ", port=" + port
                + ", database='" + database + '\''
                + ", userName='" + userName + '\''
                + ", password='" + password + '\''
                + ", connectType=" + connectType
                + ", other='" + other + '\''
                + '}';
    }

    @Override
    public DbType getType() {
        return DbType.ORACLE;
    }
}
