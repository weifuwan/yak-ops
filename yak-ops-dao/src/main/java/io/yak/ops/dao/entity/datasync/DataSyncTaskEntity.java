package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射数据同步专属配置表。Name/Status/Version/Remark 属于关联的通用 Definition，\n * 在此作为 DAO 查询投影，不参与插件配置表读写。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_task")
public class DataSyncTaskEntity extends BaseEntity {

    /** 任务所属 Workspace ID。 */
    private String workspaceId;

    /** 同一 Workspace 内唯一的任务名称。 */
    @TableField(exist = false)
    private String name;

    /** 数据同步类型：OFFLINE 或 REALTIME。 */
    private DataSyncType syncType;

    /** 任务发布状态：UNPUBLISHED 或 PUBLISHED。 */
    @TableField(exist = false)
    private DataSyncTaskStatus status;

    /** REALTIME 用户期望运行状态；OFFLINE 固定为 STOPPED。 */
    private DataSyncDesiredState desiredState;

    /** 离线同步写入方式；REALTIME 当前固定保存为 APPEND。 */
    private DataSyncWriteMode writeMode;

    /** 来源数据源 ID。 */
    private String sourceDataSourceId;

    /** 来源数据库名称。 */
    private String sourceDatabase;

    /** 来源 Schema 名称。 */
    private String sourceSchema;

    /** 来源表名称。 */
    private String sourceTable;

    /** 目标数据源 ID。 */
    private String targetDataSourceId;

    /** 目标数据库名称。 */
    private String targetDatabase;

    /** 目标 Schema 名称。 */
    private String targetSchema;

    /** 目标表名称。 */
    private String targetTable;

    /** 目标表不存在时是否允许按 LogicalTable 自动建表；旧任务默认 false。 */
    private Boolean autoCreateTable;

    /** 任务级字段映射 JSON；NULL 表示沿用大小写不敏感同名映射。 */
    private String mappingConfig;

    /** 按 syncType 持久化的 YakFlow 运行参数 JSON，不包含数据源连接凭证。 */
    private String runtimeConfig;

    /** 执行重试策略 JSON，包含 maxAttempts 与 backoffSeconds。 */
    private String retryPolicy;

    /** 当前任务定义版本，从 1 开始递增。 */
    @TableField(exist = false)
    private Integer definitionVersion;

    /** 用户维护的任务备注。 */
    @TableField(exist = false)
    private String remark;
}
