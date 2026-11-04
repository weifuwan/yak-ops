package io.yak.ops.plugin.datasource.starrocks.param;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.spi.enums.DbType;

public class StarRocksDataSourceParamDTO extends BaseDataSourceParamDTO {

    @Override
    public String toString() {
        return "StarRocksDataSourceParamDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", note='" + note + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", userName='" + userName + '\'' +
                ", other=" + other +
                '}';
    }

    @Override
    public DbType getType() {
        return DbType.STARROCKS;
    }
}
