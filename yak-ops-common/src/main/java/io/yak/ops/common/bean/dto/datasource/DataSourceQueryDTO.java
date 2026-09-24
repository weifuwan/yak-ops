package io.yak.ops.common.bean.dto.datasource;

import io.yak.ops.common.constant.datasource.DataSourceConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据源管理列表的分页和筛选参数。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
public class DataSourceQueryDTO {

    /** 当前页码，从 1 开始。 */
    @Min(value = 1, message = "页码必须大于 0")
    private int pageNo = DataSourceConstants.DEFAULT_PAGE_NO;

    /** 每页条数，最大 200。 */
    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = DataSourceConstants.MAX_PAGE_SIZE, message = "每页条数不能超过 200")
    private int pageSize = DataSourceConstants.DEFAULT_PAGE_SIZE;

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
