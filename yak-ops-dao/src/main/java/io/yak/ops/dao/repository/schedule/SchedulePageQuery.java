package io.yak.ops.dao.repository.schedule;

import io.yak.ops.common.enums.schedule.ScheduleTargetType;

/**
 * Validated Workspace page selection.
 * @param workspaceId authorized Workspace
 * @param pageNo one-based page number
 * @param pageSize bounded page size
 * @param targetType optional target category
 * @param targetId optional target identifier
 * @param enabled optional desired enablement
 */
public record SchedulePageQuery(
        String workspaceId,
        int pageNo,
        int pageSize,
        ScheduleTargetType targetType,
        String targetId,
        Boolean enabled) {}
