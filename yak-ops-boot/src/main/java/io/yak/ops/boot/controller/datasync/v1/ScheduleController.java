package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncScheduleService;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATA_SYNC离线Cron配置、预览及启停，保持已有Quartz行为。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "数据同步调度")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class ScheduleController {

    @Resource
    private DataSyncScheduleService service;

    @Operation(summary = "预览离线同步调度未来触发时间")
    @PostMapping("/schedules/preview")
    public Result<DataSyncSchedulePreviewVO> previewSchedule(@Valid @RequestBody DataSyncScheduleDTO dto) {
        return Result.success(service.previewSchedule(dto));
    }

    @Operation(summary = "保存离线同步任务调度")
    @PutMapping("/tasks/{id}/schedule")
    public Result<DataSyncScheduleVO> saveSchedule(
            @PathVariable("id") String id, @Valid @RequestBody DataSyncScheduleDTO dto) {
        return Result.success(service.saveSchedule(id, dto));
    }

    @Operation(summary = "查询离线同步任务调度")
    @GetMapping("/tasks/{id}/schedule")
    public Result<DataSyncScheduleVO> scheduleDetail(@PathVariable("id") String id) {
        return Result.success(service.querySchedule(id));
    }

    @Operation(summary = "启用离线同步任务调度")
    @PostMapping("/tasks/{id}/schedule/enable")
    public Result<DataSyncScheduleVO> enableSchedule(@PathVariable("id") String id) {
        return Result.success(service.enableSchedule(id));
    }

    @Operation(summary = "停用离线同步任务调度")
    @PostMapping("/tasks/{id}/schedule/disable")
    public Result<DataSyncScheduleVO> disableSchedule(@PathVariable("id") String id) {
        return Result.success(service.disableSchedule(id));
    }

}
