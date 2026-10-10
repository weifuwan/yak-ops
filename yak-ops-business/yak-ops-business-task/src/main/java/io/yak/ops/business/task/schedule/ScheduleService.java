package io.yak.ops.business.task.schedule;

import io.yak.ops.common.bean.vo.task.ScheduleVO;

/**
 * 通用Schedule的持久化定义只读入口；创建启停仍在当前DATA_SYNC业务中。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface ScheduleService {

    /** 查询当前Workspace中的Task Schedule；未配置时返回null。 */
    ScheduleVO queryTaskSchedule(String taskId);
}
