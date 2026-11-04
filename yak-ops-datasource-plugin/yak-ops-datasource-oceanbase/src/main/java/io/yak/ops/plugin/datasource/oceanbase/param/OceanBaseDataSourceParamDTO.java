package io.yak.ops.plugin.datasource.oceanbase.param;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.spi.enums.DbType;

public class OceanBaseDataSourceParamDTO extends BaseDataSourceParamDTO {

    protected String compatibleMode;

    public String getCompatibleMode() {
        return compatibleMode;
    }

    public void setCompatibleMode(String compatibleMode) {
        this.compatibleMode = compatibleMode;
    }

    @Override
    public DbType getType() {
        return DbType.OCEANBASE;
    }
}
