package io.yak.ops.plugin.datasource.oceanbase.param;

import io.yak.ops.spi.datasource.BaseConnectionParam;

import lombok.Data;

@Data
public class OceanBaseConnectionParam extends BaseConnectionParam {

    protected String compatibleMode;
}
