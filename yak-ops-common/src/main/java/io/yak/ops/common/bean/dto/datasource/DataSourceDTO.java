package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 数据源新增和编辑数据传输对象。 */
@Data
public class DataSourceDTO {

    /** 数据源名称。 */
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 128, message = "数据源名称不能超过 128 个字符")
    private String name;

    /** 数据库类型。 */
    @NotBlank(message = "数据源类型不能为空")
    private String dbType;

    /** 运行环境。 */
    private String environment;

    /** 备注。 */
    @Size(max = 500, message = "数据源备注不能超过 500 个字符")
    private String remark;

    /** 前端动态表单提交的连接参数 JSON。 */
    @NotBlank(message = "数据源连接参数不能为空")
    private String connectionParams;
}
