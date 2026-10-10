package io.yak.ops.boot.controller.task.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.task.log.LogService;
import io.yak.ops.common.bean.vo.task.EventVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用实例结构化事件接口；日志文件读取须等后续Worker日志存储合同落地。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "任务日志")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-instances")
public class TaskLogController {

    @Resource
    private LogService logService;

    @Operation(summary = "查询任务实例结构化事件")
    @GetMapping("/{id}/events")
    public Result<List<EventVO>> events(@PathVariable("id") String id) {
        return Result.success(logService.queryEvents(id));
    }
}
