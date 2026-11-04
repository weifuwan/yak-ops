package io.yak.ops.plugin.datasource.api.datasource;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class BaseHDFSDataSourceParamDTO extends BaseDataSourceParamDTO {

    protected String principal;

    protected String javaSecurityKrb5Conf;

    protected String loginUserKeytabUsername;

    protected String loginUserKeytabPath;

}
