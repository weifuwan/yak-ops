package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 数据同步任务定义响应。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncTaskVO {

    /** 数据同步任务 ID。 */
    private String id;

    /** 同一 Workspace 内的任务名称。 */
    private String name;

    /** 数据同步类型：OFFLINE 或 REALTIME。 */
    private String syncType;

    /** 任务发布状态：UNPUBLISHED 或 PUBLISHED。 */
    private String status;

    /** REALTIME 期望运行状态：STOPPED / RUNNING；OFFLINE 固定为 STOPPED。 */
    private String desiredState;

    /** 离线同步写入方式：APPEND / OVERWRITE / UPSERT；REALTIME 当前固定为 APPEND。 */
    private String writeMode;

    /** 来源数据源 ID。 */
    private String sourceDataSourceId;

    /** 来源数据源绑定或任务解析后的数据库名称；没有该层级时为空。 */
    private String sourceDatabase;

    /** 来源实际使用的 Schema；没有该层级时为空。 */
    private String sourceSchema;

    /** 来源物理表名称。 */
    private String sourceTable;

    /** 目标数据源 ID。 */
    private String targetDataSourceId;

    /** 目标数据源绑定或任务解析后的数据库名称；没有该层级时为空。 */
    private String targetDatabase;

    /** 目标实际使用的 Schema；没有该层级时为空。 */
    private String targetSchema;

    /** 目标物理表名称。 */
    private String targetTable;

    /**
     * 历史 Route 响应字段。当前单表任务不再生成 Route；旧 Execution 快照独立保留。
     */
    private List<DataSyncTableRouteVO> tableRoutes;

    /** OFFLINE 任务的 YakFlow 读取、写入和超时参数；REALTIME 任务为空。 */
    private DataSyncRuntimeConfigVO runtimeConfig;

    /** REALTIME 任务的 CDC、Checkpoint 和写入参数；OFFLINE 任务为空。 */
    private DataSyncRealtimeConfigVO realtimeConfig;

    /** Execution 失败后的固定重试策略。 */
    private DataSyncRetryPolicyVO retryPolicy;

    /** 当前任务定义版本，从 1 开始，任务定义每次成功修改后递增。 */
    private Integer definitionVersion;

    /** 用户维护的任务说明。 */
    private String remark;

    /** 任务最近一次更新操作人标识。 */
    private String updateBy;

    /** OFFLINE 任务配置的 Quartz Cron；未配置 Schedule 时为空。 */
    private String scheduleCronExpression;

    /** OFFLINE 任务 Schedule 的显式 IANA Time Zone；未配置 Schedule 时为空。 */
    private String scheduleTimeZone;

    /** OFFLINE 任务 Schedule 是否已启用；未配置 Schedule 时为空。 */
    private Boolean scheduleEnabled;

    /** 任务创建时间。 */
    private LocalDateTime createTime;

    /** 任务定义最近一次更新时间。 */
    private LocalDateTime updateTime;
}
