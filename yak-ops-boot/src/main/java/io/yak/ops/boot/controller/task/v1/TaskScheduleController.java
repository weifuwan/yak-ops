package io.yak.ops.boot.controller.task.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.task.schedule.ScheduleService;
import io.yak.ops.common.bean.vo.task.ScheduleVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用Task Schedule的持久化定义读取，不提供尚未实现的Workflow调度启停。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "任务调度")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-schedules")
public class TaskScheduleController {

    @Resource
    private ScheduleService scheduleService;

    @Operation(summary = "查询任务的通用Schedule")
    @GetMapping("/tasks/{taskId}")
    public Result<ScheduleVO> taskSchedule(@PathVariable("taskId") String taskId) {
        return Result.success(scheduleService.queryTaskSchedule(taskId));
    }
}
