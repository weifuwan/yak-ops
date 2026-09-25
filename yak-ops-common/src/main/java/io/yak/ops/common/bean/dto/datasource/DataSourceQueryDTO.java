package io.yak.ops.common.bean.dto.datasource;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据源管理列表的分页和筛选参数。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceQueryDTO extends PageQueryDTO {

    /** 兼容旧调用方的数据源名称筛选。 */
    @Size(max = 128, message = "数据源名称筛选不能超过 128 个字符")
    private String name;

    /** 数据源名称、连接地址、类型和环境的统一搜索词。 */
    @Size(max = 256, message = "搜索关键词不能超过 256 个字符")
    private String keyword;

    /** 数据库类型筛选条件。 */
    private String dbType;

    /** 运行环境筛选条件。 */
    private String environment;

    /** 连通状态筛选条件，例如 CONNECTED、DISCONNECTED、UNKNOWN。 */
    private String connStatus;
}
