package io.yak.ops.dao.repository.task;

import io.yak.ops.common.enums.task.InstanceStatus;

/**
 * 通用任务实例分页筛选条件；当前Workspace身份由Service独立传入。
 *
 * @param pageNo 当前页码，从1开始
 * @param pageSize 每页记录数
 * @param taskId 任务定义ID，可为空
 * @param taskType 插件canonical type，可为空
 * @param status 实例生命周期状态，可为空
 * @author weifuwan
 * @since 2026-10-10
 */
public record InstancePageQuery(int pageNo, int pageSize, String taskId, String taskType, InstanceStatus status) {}
