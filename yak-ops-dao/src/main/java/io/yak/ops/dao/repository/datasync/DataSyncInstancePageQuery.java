package io.yak.ops.dao.repository.datasync;

import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import java.time.LocalDateTime;

/**
 * 数据同步实例 Repository 分页筛选条件。
 *
 * @param pageNo 当前页码
 * @param pageSize 每页数量
 * @param taskId 任务 ID
 * @param keyword 任务名称关键字
 * @param syncType 实例同步类型
 * @param status 实例状态
 * @param triggerType 触发方式
 * @param startTimeStart 实际开始时间下界
 * @param startTimeEnd 实际开始时间上界
 * @author weifuwan
 * @since 2026-09-27
 */
public record DataSyncInstancePageQuery(
        int pageNo,
        int pageSize,
        String taskId,
        String keyword,
        DataSyncType syncType,
        DataSyncInstanceStatus status,
        DataSyncTriggerType triggerType,
        LocalDateTime startTimeStart,
        LocalDateTime startTimeEnd) {}
