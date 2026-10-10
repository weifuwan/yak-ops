package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncInstanceService;
import io.yak.ops.business.datasync.DataSyncOperationsService;
import io.yak.ops.business.datasync.DataSyncScheduleService;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供数据同步实例、调度和运维指标 HTTP 接口。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Tag(name = "数据同步任务接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class DataSyncController {

    @Resource
    private DataSyncInstanceService instanceService;

    @Resource
    private DataSyncScheduleService scheduleService;

    @Resource
    private DataSyncOperationsService operationsService;

    @Operation(summary = "分页查询运维中心数据同步任务运行态")
    @PostMapping("/operations/tasks/page")
    public Result<PagingData<DataSyncTaskOperationVO>> taskOperationPage(@Valid @RequestBody DataSyncTaskQueryDTO dto) {
        return Result.success(operationsService.queryTaskOperationPage(dto));
    }

    @Operation(summary = "查询运维中心数据同步聚合指标")
    @PostMapping("/operations/dashboard")
    public Result<DataSyncOperationsDashboardVO> operationsDashboard(
            @Valid @RequestBody DataSyncOperationsDashboardDTO dto) {
        return Result.success(operationsService.queryOperationsDashboard(dto));
    }

    @Operation(summary = "手动运行数据同步任务")
    @PostMapping("/tasks/{id}/run")
    public Result<DataSyncInstanceVO> runTask(@PathVariable("id") String id) {
        return Result.success(instanceService.runTask(id));
    }

    @Operation(summary = "预览离线同步调度未来触发时间")
    @PostMapping("/schedules/preview")
    public Result<DataSyncSchedulePreviewVO> previewSchedule(@Valid @RequestBody DataSyncScheduleDTO dto) {
        return Result.success(scheduleService.previewSchedule(dto));
    }

    @Operation(summary = "保存离线同步任务调度")
    @PutMapping("/tasks/{id}/schedule")
    public Result<DataSyncScheduleVO> saveSchedule(
            @PathVariable("id") String id, @Valid @RequestBody DataSyncScheduleDTO dto) {
        return Result.success(scheduleService.saveSchedule(id, dto));
    }

    @Operation(summary = "查询离线同步任务调度")
    @GetMapping("/tasks/{id}/schedule")
    public Result<DataSyncScheduleVO> scheduleDetail(@PathVariable("id") String id) {
        return Result.success(scheduleService.querySchedule(id));
    }

    @Operation(summary = "启用离线同步任务调度")
    @PostMapping("/tasks/{id}/schedule/enable")
    public Result<DataSyncScheduleVO> enableSchedule(@PathVariable("id") String id) {
        return Result.success(scheduleService.enableSchedule(id));
    }

    @Operation(summary = "停用离线同步任务调度")
    @PostMapping("/tasks/{id}/schedule/disable")
    public Result<DataSyncScheduleVO> disableSchedule(@PathVariable("id") String id) {
        return Result.success(scheduleService.disableSchedule(id));
    }

    @Operation(summary = "查询同步实例详情")
    @GetMapping("/instances/{id}")
    public Result<DataSyncInstanceVO> instanceDetail(@PathVariable("id") String id) {
        return Result.success(instanceService.queryInstance(id));
    }

    @Operation(summary = "查询同步Execution的Attempt历史")
    @GetMapping("/instances/{id}/attempts")
    public Result<List<DataSyncAttemptVO>> attemptHistory(@PathVariable("id") String id) {
        return Result.success(instanceService.queryAttempts(id));
    }

    @Operation(summary = "查询单表同步Attempt历史")
    @GetMapping("/instances/{id}/tables/{tableExecutionId}/attempts")
    public Result<List<DataSyncTableAttemptVO>> tableAttemptHistory(
            @PathVariable("id") String id, @PathVariable("tableExecutionId") String tableExecutionId) {
        return Result.success(instanceService.queryTableAttempts(id, tableExecutionId));
    }

    @Operation(summary = "查询同步Execution产品事件")
    @GetMapping("/instances/{id}/logs")
    public Result<List<DataSyncExecutionEventVO>> executionLogs(@PathVariable("id") String id) {
        return Result.success(instanceService.queryExecutionEvents(id));
    }

    @Operation(summary = "查询离线同步Execution Runtime Trace汇总")
    @GetMapping("/instances/{id}/trace/summary")
    public Result<DataSyncTraceSummaryVO> executionTraceSummary(
            @PathVariable("id") String id, @RequestParam(value = "attemptNo", required = false) Integer attemptNo) {
        return Result.success(instanceService.queryExecutionTraceSummary(id, attemptNo));
    }

    @Operation(summary = "Cursor分页查询离线同步Source Split诊断")
    @GetMapping("/instances/{id}/trace/source")
    public Result<DataSyncTracePageVO<DataSyncSourceTraceVO>> executionSourceTrace(
            @PathVariable("id") String id,
            @RequestParam(value = "attemptNo", required = false) Integer attemptNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(instanceService.queryExecutionSourceTrace(id, attemptNo, pageSize, cursor, status));
    }

    @Operation(summary = "Cursor分页查询离线同步Sink Batch诊断")
    @GetMapping("/instances/{id}/trace/sink")
    public Result<DataSyncTracePageVO<DataSyncSinkTraceVO>> executionSinkTrace(
            @PathVariable("id") String id,
            @RequestParam(value = "attemptNo", required = false) Integer attemptNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(instanceService.queryExecutionSinkTrace(id, attemptNo, pageSize, cursor, status));
    }

    @Operation(summary = "分页查询同步实例")
    @PostMapping("/instances/page")
    public Result<PagingData<DataSyncInstanceVO>> instancePage(@Valid @RequestBody DataSyncInstanceQueryDTO dto) {
        return Result.success(instanceService.queryInstancePage(dto));
    }

    @Operation(summary = "停止同步实例")
    @PostMapping("/instances/{id}/cancel")
    public Result<DataSyncInstanceVO> cancelInstance(@PathVariable("id") String id) {
        return Result.success(instanceService.cancelInstance(id));
    }
}
