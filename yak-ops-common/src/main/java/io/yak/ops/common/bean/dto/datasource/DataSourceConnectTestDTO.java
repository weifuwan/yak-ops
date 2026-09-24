package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
    @Positive(message = "dataSourceId 必须大于 0")
    private Long dataSourceId;

    /** 可选的数据源类型；未提供时从 connJson 的 dbType/type/pluginType 路由。 */
    private String dbType;

    /** 动态表单连接参数 JSON。 */
    @NotBlank(message = "connJson 不能为空")
    private String connJson;
}
