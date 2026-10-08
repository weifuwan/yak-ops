package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 数据同步任务定义的创建与编辑请求。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncTaskDTO {

    /** 同一 Workspace 内唯一的任务名称。 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称不能超过 128 个字符")
    private String name;

    /** 同步任务类型，支持 OFFLINE 与 REALTIME。 */
    @NotNull(message = "同步类型不能为空")
    private DataSyncType syncType = DataSyncType.OFFLINE;

    /** 离线同步目标数据写入方式；当前阶段仅开放 APPEND。 */
    @NotNull(message = "写入方式不能为空")
    private DataSyncWriteMode writeMode = DataSyncWriteMode.APPEND;

    /** 来源数据源 ID。 */
    @NotBlank(message = "来源数据源不能为空")
    private String sourceDataSourceId;

    /** 来源数据库名称，无该层级时为空。 */
    @Size(max = 128, message = "来源数据库名称不能超过 128 个字符")
    private String sourceDatabase;

    /** 来源 Schema 名称，无该层级时为空。 */
    @Size(max = 128, message = "来源 Schema 名称不能超过 128 个字符")
    private String sourceSchema;

    /** 来源表名称。 */
    @NotBlank(message = "来源表不能为空")
    @Size(max = 128, message = "来源表名称不能超过 128 个字符")
    private String sourceTable;

    /** 目标数据源 ID。 */
    @NotBlank(message = "目标数据源不能为空")
    private String targetDataSourceId;

    /** 目标数据库名称，无该层级时为空。 */
    @Size(max = 128, message = "目标数据库名称不能超过 128 个字符")
    private String targetDatabase;

    /** 目标 Schema 名称，无该层级时为空。 */
    @Size(max = 128, message = "目标 Schema 名称不能超过 128 个字符")
    private String targetSchema;

    /** 目标表名称。 */
    @NotBlank(message = "目标表不能为空")
    @Size(max = 128, message = "目标表名称不能超过 128 个字符")
    private String targetTable;

    /** 目标表不存在时是否允许按 LogicalTable 自动建表；默认关闭。 */
    private Boolean autoCreateTable = Boolean.FALSE;

    /** 任务级显式字段映射；为空时继续使用系统默认同名映射。 */
    @Valid
    private DataSyncMappingDTO mapping;

    /**
     * OFFLINE Task 的有序表级定义。null 保持 v1.2 单表兼容；显式传入时必须 1-50 条，
     * Source / Target Datasource 仍由 Task 共享。
     */
    @Valid
    @Size(min = 1, max = 50, message = "表级路由数量必须在 1 到 50 之间")
    private List<DataSyncTableRouteDTO> tableRoutes;

    /**
     * OFFLINE 任务使用的 YakFlow 运行参数。
     *
     * <p>请求可省略：创建时由服务端物化系统默认值，编辑时省略表示保留已有配置。</p>
     */
    @Valid
    private DataSyncRuntimeConfigDTO runtimeConfig;

    /**
     * REALTIME 任务使用的 YakFlow CDC 运行参数。
     *
     * <p>请求可省略：创建时由服务端物化系统默认值，编辑时省略表示保留已有配置。</p>
     */
    @Valid
    private DataSyncRealtimeConfigDTO realtimeConfig;

    /**
     * Execution 失败后的固定重试策略。
     *
     * <p>请求可省略：创建时由服务端物化系统默认值，编辑时省略表示保留已有配置。</p>
     */
    @Valid
    private DataSyncRetryPolicyDTO retryPolicy;

    /** 用户维护的任务备注。 */
    @Size(max = 500, message = "任务备注不能超过 500 个字符")
    private String remark;
}
