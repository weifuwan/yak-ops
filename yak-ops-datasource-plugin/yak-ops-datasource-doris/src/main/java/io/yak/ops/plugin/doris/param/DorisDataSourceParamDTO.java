package io.yak.ops.plugin.doris.param;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.spi.enums.DbType;

public class DorisDataSourceParamDTO extends BaseDataSourceParamDTO {

    @Override
    public String toString() {
        return "DorisDataSourceParamDTO{"
                + "name='" + name + '\''
                + ", note='" + note + '\''
                + ", host='" + host + '\''
                + ", port=" + port
                + ", database='" + database + '\''
                + ", userName='" + userName + '\''
                + ", password='" + password + '\''
                + ", other='" + other + '\''
                + '}';
    }
    @Override
    public DbType getType() {
        return DbType.DORIS;
    }
}
