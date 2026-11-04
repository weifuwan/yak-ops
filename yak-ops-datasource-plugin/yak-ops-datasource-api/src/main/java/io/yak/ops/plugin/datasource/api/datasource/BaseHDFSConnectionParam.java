package io.yak.ops.plugin.datasource.api.datasource;

import io.yak.ops.spi.datasource.BaseConnectionParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseHDFSConnectionParam extends BaseConnectionParam {

    protected String principal;
    protected String javaSecurityKrb5Conf;
    protected String loginUserKeytabUsername;
    protected String loginUserKeytabPath;

}
