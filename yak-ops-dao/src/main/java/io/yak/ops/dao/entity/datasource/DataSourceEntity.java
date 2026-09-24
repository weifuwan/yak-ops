package io.yak.ops.dao.entity.datasource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_source 表，承载数据源持久化状态。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_source")
public class DataSourceEntity {

    /** 数据源主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源名称，在当前产品范围内唯一。 */
    private String name;

    /** 数据库类型，用于选择对应的数据源插件能力。 */
    private DataSourceDbType dbType;

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

    /** 数据源记录创建时间。 */
    private LocalDateTime createTime;

    /** 数据源记录最后更新时间。 */
    private LocalDateTime updateTime;
}
