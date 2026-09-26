package io.yak.ops.dao.entity.datasource;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_source 表，承载 Workspace 内的数据源持久化状态。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_source")
public class DataSourceEntity extends BaseEntity {

    /** 数据源所属 Workspace ID，是数据源业务归属与隔离边界。 */
    private String workspaceId;

    /** 数据源名称，在同一 Workspace 内唯一。 */
    private String name;

    /** 数据库类型，用于选择对应的数据源插件能力。 */
    private String dbType;

    /** JDBC 连接地址。 */
    private String jdbcUrl;

    /** 数据源所属运行环境。 */
    private DataSourceEnvironment environment;

    /** 最近一次连接检测得到的连通状态。 */
    private DataSourceConnStatus connStatus;

    /** 用户维护的数据源备注。 */
    private String remark;

    /** 规范化后的连接参数 JSON，包含敏感连接信息。 */
    @ToString.Exclude
    private String connectionParams;

    /** 前端编辑回显使用的原始配置 JSON，可能包含敏感连接信息。 */
    @ToString.Exclude
    private String originalJson;
}
