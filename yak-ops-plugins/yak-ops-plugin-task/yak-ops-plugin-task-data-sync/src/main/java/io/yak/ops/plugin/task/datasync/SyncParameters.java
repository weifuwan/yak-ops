package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginException;

/**
 * DATA_SYNC 任务专属的单表配置。名称、版本与发布状态归通用 TaskDefinition。
 *
 * <p>Runtime 与 Retry 只做 JSON 对象形状校验；Datasource 授权、物理 Schema 和执行策略仍由业务层负责。
 *
 * @param syncType OFFLINE 有界同步或 REALTIME 持续同步
 * @param writeMode 目标写入模式；REALTIME 当前仅支持 APPEND
 * @param sourceDataSourceId 来源数据源稳定资源 ID，不包含连接凭证
 * @param sourceDatabase 来源数据库名称，缺少该层级时为空
 * @param sourceSchema 来源 Schema 名称，缺少该层级时为空
 * @param sourceTable 来源物理表名称
 * @param targetDataSourceId 目标数据源稳定资源 ID，不包含连接凭证
 * @param targetDatabase 目标数据库名称，缺少该层级时为空
 * @param targetSchema 目标 Schema 名称，缺少该层级时为空
 * @param targetTable 目标物理表名称
 * @param runtimeConfig 离线执行配置 JSON 对象
 * @param realtimeConfig 实时 CDC 配置 JSON 对象
 * @param retryPolicy 业务任务重试策略 JSON 对象
 * @author weifuwan
 * @since 2026-10-10
 */
public record SyncParameters(
        DataSyncType syncType,
        DataSyncWriteMode writeMode,
        String sourceDataSourceId,
        String sourceDatabase,
        String sourceSchema,
        String sourceTable,
        String targetDataSourceId,
        String targetDatabase,
        String targetSchema,
        String targetTable,
        JsonNode runtimeConfig,
        JsonNode realtimeConfig,
        JsonNode retryPolicy)
        implements TaskParameters {

    @Override
    public void validate() {
        if (syncType == null || writeMode == null) {
            throw new TaskPluginException("DATA_SYNC requires syncType and writeMode");
        }
        if (syncType == DataSyncType.REALTIME && writeMode != DataSyncWriteMode.APPEND) {
            throw new TaskPluginException("REALTIME DATA_SYNC requires APPEND write mode");
        }
        requireText(sourceDataSourceId, "sourceDataSourceId", 64);
        requireText(sourceTable, "sourceTable", 128);
        requireText(targetDataSourceId, "targetDataSourceId", 64);
        requireText(targetTable, "targetTable", 128);
        checkOptionalText(sourceDatabase, "sourceDatabase", 128);
        checkOptionalText(sourceSchema, "sourceSchema", 128);
        checkOptionalText(targetDatabase, "targetDatabase", 128);
        checkOptionalText(targetSchema, "targetSchema", 128);
        checkObject(runtimeConfig, "runtimeConfig");
        checkObject(realtimeConfig, "realtimeConfig");
        checkObject(retryPolicy, "retryPolicy");
    }

    private static void requireText(String value, String field, int maxLength) {
        if (StringUtils.isBlank(value)) {
            throw new TaskPluginException("DATA_SYNC requires " + field);
        }
        checkOptionalText(value, field, maxLength);
    }

    private static void checkOptionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new TaskPluginException("DATA_SYNC " + field + " exceeds maximum length " + maxLength);
        }
    }

    private static void checkObject(JsonNode value, String field) {
        if (value != null && !value.isNull() && !value.isObject()) {
            throw new TaskPluginException("DATA_SYNC " + field + " must be a JSON object");
        }
    }
}
