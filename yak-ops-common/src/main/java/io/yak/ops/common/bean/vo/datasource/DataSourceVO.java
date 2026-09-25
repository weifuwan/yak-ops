package io.yak.ops.common.bean.vo.datasource;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据源管理列表和详情的展示对象。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Data
public class DataSourceVO {

    /** 数据源主键 ID。 */
    private String id;

    /** 数据源名称。 */
    private String name;

    /** 数据库类型。 */
    private String dbType;

    /** 已遮罩敏感信息的连接地址。 */
    private String jdbcUrl;

    /** 运行环境编码。 */
    private String environment;

    /** 运行环境展示名称。 */
    private String environmentName;

    /** 最近一次连接状态。 */
    private String connStatus;

    /** 数据源备注。 */
    private String remark;

    /** 详情编辑回显使用的已遮罩连接参数 JSON。 */
    private String originalJson;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后更新时间。 */
    private LocalDateTime updateTime;
}
