package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 未保存或编辑态数据源的连接测试参数。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
public class DataSourceConnectTestDTO {

    /** 编辑已有数据源时传入，用于保留未修改的敏感字段。 */
    private String dataSourceId;

    /** 数据源类型，用于路由到对应 Provider。 */
    @NotBlank(message = "数据源类型不能为空")
    private String dbType;

    /** 与新增 / 编辑共用的结构化连接参数。 */
    @Valid
    @NotNull(message = "数据源连接参数不能为空")
    private DataSourceConnectionDTO connectionParams;
}
