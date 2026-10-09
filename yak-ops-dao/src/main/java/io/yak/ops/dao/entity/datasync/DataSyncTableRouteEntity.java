package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_sync_table_route 表，保存 Data Sync Task 内稳定的表级路由定义。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_table_route")
public class DataSyncTableRouteEntity extends BaseEntity {

    /** Route 所属 Workspace ID。 */
    private String workspaceId;

    /** Route 所属 Data Sync Task ID。 */
    private String taskId;

    /** 来源数据库名称，无该层级时为空。 */
    private String sourceDatabase;

    /** 来源 Schema 名称，无该层级时为空。 */
    private String sourceSchema;

    /** 来源物理表名称。 */
    private String sourceTable;

    /** 目标数据库名称，无该层级时为空。 */
    private String targetDatabase;

    /** 目标 Schema 名称，无该层级时为空。 */
    private String targetSchema;

    /** 目标物理表名称。 */
    private String targetTable;

    /** 目标表不存在时是否允许按当前 Route 的 LogicalTable 自动建表。 */
    private Boolean autoCreateTable;

    /** Route 级字段映射 JSON；NULL 表示沿用大小写不敏感同名映射。 */
    private String mappingConfig;

    /** Task 内稳定展示与执行顺序，从 0 开始。 */
    private Integer sortOrder;
}
