package io.yak.ops.plugin.datasource.ssh.param;

import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.spi.enums.DbType;

import lombok.Data;

@Data
public class SSHDataSourceParamDTO extends BaseDataSourceParamDTO {

    protected String privateKey;

    @Override
    public DbType getType() {
        return DbType.SSH;

    }
}
