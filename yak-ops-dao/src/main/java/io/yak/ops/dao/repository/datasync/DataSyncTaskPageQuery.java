package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;

/**
 * 数据同步任务 Repository 分页筛选条件。
 *
 * @param pageNo 当前页码
 * @param pageSize 每页数量
 * @param keyword 任务名称、来源表或目标表关键字
 * @param syncType 同步类型
 * @param status 任务发布状态
 * @param sourceDataSourceId 来源数据源 ID
 * @param targetDataSourceId 目标数据源 ID
 * @author weifuwan
 * @since 2026-09-27
 */
public record DataSyncTaskPageQuery(
        int pageNo,
        int pageSize,
        String keyword,
        DataSyncType syncType,
        DataSyncTaskStatus status,
        String sourceDataSourceId,
        String targetDataSourceId) {}
