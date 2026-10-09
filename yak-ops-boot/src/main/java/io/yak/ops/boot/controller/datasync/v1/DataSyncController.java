package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncScheduleDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSchedulePreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供数据同步任务定义、实例与字段映射预览 HTTP 接口。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Tag(name = "数据同步任务接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class DataSyncController {

    @Resource
    private DataSyncService dataSyncService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Operation(summary = "创建数据同步任务")
    @PostMapping("/tasks")
    public Result<DataSyncTaskVO> createTask(@Valid @RequestBody DataSyncTaskDTO dto) {
        return Result.success(dataSyncService.createTask(dto, currentUserId()));
    }

    @Operation(summary = "编辑数据同步任务")
    @PutMapping("/tasks/{id}")
    public Result<DataSyncTaskVO> updateTask(@PathVariable("id") String id, @Valid @RequestBody DataSyncTaskDTO dto) {
        return Result.success(dataSyncService.updateTask(id, dto, currentUserId()));
    }

    @Operation(summary = "查询数据同步任务详情")
    @GetMapping("/tasks/{id}")
    public Result<DataSyncTaskVO> taskDetail(@PathVariable("id") String id) {
        return Result.success(dataSyncService.queryTask(id));
    }

    @Operation(summary = "分页查询数据同步任务")
    @PostMapping("/tasks/page")
    public Result<PagingData<DataSyncTaskVO>> taskPage(@Valid @RequestBody DataSyncTaskQueryDTO dto) {
        return Result.success(dataSyncService.queryTaskPage(dto));
    }

    @Operation(summary = "分页查询运维中心数据同步任务运行态")
    @PostMapping("/operations/tasks/page")
    public Result<PagingData<DataSyncTaskOperationVO>> taskOperationPage(@Valid @RequestBody DataSyncTaskQueryDTO dto) {
        return Result.success(dataSyncService.queryTaskOperationPage(dto));
    }

    @Operation(summary = "查询运维中心数据同步聚合指标")
    @PostMapping("/operations/dashboard")
    public Result<DataSyncOperationsDashboardVO> operationsDashboard(
            @Valid @RequestBody DataSyncOperationsDashboardDTO dto) {
        return Result.success(dataSyncService.queryOperationsDashboard(dto));
    }

    @Operation(summary = "上线数据同步任务")
    @PostMapping("/tasks/{id}/publish")
    public Result<DataSyncTaskVO> publishTask(@PathVariable("id") String id) {
        return Result.success(dataSyncService.publishTask(id, currentUserId()));
    }

    @Operation(summary = "下线数据同步任务")
    @PostMapping("/tasks/{id}/unpublish")
    public Result<DataSyncTaskVO> unpublishTask(@PathVariable("id") String id) {
        return Result.success(dataSyncService.unpublishTask(id, currentUserId()));
    }

    @Operation(summary = "手动运行数据同步任务")
    @PostMapping("/tasks/{id}/run")
    public Result<DataSyncInstanceVO> runTask(@PathVariable("id") String id) {
        return Result.success(dataSyncService.runTask(id));
    }

    @Operation(summary = "预览离线同步调度未来触发时间")
    @PostMapping("/schedules/preview")
    public Result<DataSyncSchedulePreviewVO> previewSchedule(@Valid @RequestBody DataSyncScheduleDTO dto) {
        return Result.success(dataSyncService.previewSchedule(dto));
    }

    @Operation(summary = "保存离线同步任务调度")
    @PutMapping("/tasks/{id}/schedule")
    public Result<DataSyncScheduleVO> saveSchedule(
            @PathVariable("id") String id, @Valid @RequestBody DataSyncScheduleDTO dto) {
        return Result.success(dataSyncService.saveSchedule(id, dto));
    }

    @Operation(summary = "查询离线同步任务调度")
    @GetMapping("/tasks/{id}/schedule")
    public Result<DataSyncScheduleVO> scheduleDetail(@PathVariable("id") String id) {
        return Result.success(dataSyncService.querySchedule(id));
    }

    @Operation(summary = "启用离线同步任务调度")
    @PostMapping("/tasks/{id}/schedule/enable")
    public Result<DataSyncScheduleVO> enableSchedule(@PathVariable("id") String id) {
        return Result.success(dataSyncService.enableSchedule(id));
    }

    @Operation(summary = "停用离线同步任务调度")
    @PostMapping("/tasks/{id}/schedule/disable")
    public Result<DataSyncScheduleVO> disableSchedule(@PathVariable("id") String id) {
        return Result.success(dataSyncService.disableSchedule(id));
    }

    @Operation(summary = "删除数据同步任务")
    @DeleteMapping("/tasks/{id}")
    public Result<Boolean> deleteTask(@PathVariable("id") String id) {
        return Result.success(dataSyncService.deleteTask(id));
    }

    @Operation(summary = "查询同步实例详情")
    @GetMapping("/instances/{id}")
    public Result<DataSyncInstanceVO> instanceDetail(@PathVariable("id") String id) {
        return Result.success(dataSyncService.queryInstance(id));
    }

    @Operation(summary = "查询同步Execution的Attempt历史")
    @GetMapping("/instances/{id}/attempts")
    public Result<List<DataSyncAttemptVO>> attemptHistory(@PathVariable("id") String id) {
        return Result.success(dataSyncService.queryAttempts(id));
    }

    @Operation(summary = "查询单表同步Attempt历史")
    @GetMapping("/instances/{id}/tables/{tableExecutionId}/attempts")
    public Result<List<DataSyncTableAttemptVO>> tableAttemptHistory(
            @PathVariable("id") String id, @PathVariable("tableExecutionId") String tableExecutionId) {
        return Result.success(dataSyncService.queryTableAttempts(id, tableExecutionId));
    }

    @Operation(summary = "查询同步Execution产品事件")
    @GetMapping("/instances/{id}/logs")
    public Result<List<DataSyncExecutionEventVO>> executionLogs(@PathVariable("id") String id) {
        return Result.success(dataSyncService.queryExecutionEvents(id));
    }

    @Operation(summary = "查询离线同步Execution Runtime Trace汇总")
    @GetMapping("/instances/{id}/trace/summary")
    public Result<DataSyncTraceSummaryVO> executionTraceSummary(
            @PathVariable("id") String id, @RequestParam(value = "attemptNo", required = false) Integer attemptNo) {
        return Result.success(dataSyncService.queryExecutionTraceSummary(id, attemptNo));
    }

    @Operation(summary = "Cursor分页查询离线同步Source Split诊断")
    @GetMapping("/instances/{id}/trace/source")
    public Result<DataSyncTracePageVO<DataSyncSourceTraceVO>> executionSourceTrace(
            @PathVariable("id") String id,
            @RequestParam(value = "attemptNo", required = false) Integer attemptNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(dataSyncService.queryExecutionSourceTrace(id, attemptNo, pageSize, cursor, status));
    }

    @Operation(summary = "Cursor分页查询离线同步Sink Batch诊断")
    @GetMapping("/instances/{id}/trace/sink")
    public Result<DataSyncTracePageVO<DataSyncSinkTraceVO>> executionSinkTrace(
            @PathVariable("id") String id,
            @RequestParam(value = "attemptNo", required = false) Integer attemptNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(dataSyncService.queryExecutionSinkTrace(id, attemptNo, pageSize, cursor, status));
    }

    @Operation(summary = "分页查询同步实例")
    @PostMapping("/instances/page")
    public Result<PagingData<DataSyncInstanceVO>> instancePage(@Valid @RequestBody DataSyncInstanceQueryDTO dto) {
        return Result.success(dataSyncService.queryInstancePage(dto));
    }

    @Operation(summary = "停止同步实例")
    @PostMapping("/instances/{id}/cancel")
    public Result<DataSyncInstanceVO> cancelInstance(@PathVariable("id") String id) {
        return Result.success(dataSyncService.cancelInstance(id));
    }

    @Operation(summary = "预览来源与目标表 Schema 映射")
    @PostMapping("/tasks/mapping-preview")
    public Result<DataSyncMappingPreviewVO> mappingPreview(@Valid @RequestBody DataSyncMappingPreviewDTO dto) {
        return Result.success(dataSyncService.previewMapping(dto));
    }

    private String currentUserId() {
        return authenticationManager.getLoginUserId();
    }
}
