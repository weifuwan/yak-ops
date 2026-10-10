package io.yak.ops.boot.controller.task.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.task.metrics.MetricsService;
import io.yak.ops.common.bean.vo.task.MetricsVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一Task根实例聚合指标，按Workspace/插件类型/时间范围查询。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "任务指标")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-metrics")
public class TaskMetricsController {

    @Resource
    private MetricsService metricsService;

    @Operation(summary = "聚合通用Task根实例指标")
    @GetMapping("/summary")
    public Result<MetricsVO> summary(
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "days", required = false) Integer days) {
        return Result.success(metricsService.querySummary(taskType, days));
    }
}
