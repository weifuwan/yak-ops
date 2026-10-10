package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncInstanceService;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSinkTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncSourceTraceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTableAttemptVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTracePageVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTraceSummaryVO;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATA_SYNC运行入口与历史实例查询；不实现已移除的旧Trace运行时。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "数据同步实例")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class InstanceController {

    @Resource
    private DataSyncInstanceService service;

    @Operation(summary = "手动运行数据同步任务")
    @PostMapping("/tasks/{id}/run")
    public Result<DataSyncInstanceVO> runTask(@PathVariable("id") String id) {
        return Result.success(service.runTask(id));
    }

    @Operation(summary = "查询同步实例详情")
    @GetMapping("/instances/{id}")
    public Result<DataSyncInstanceVO> instanceDetail(@PathVariable("id") String id) {
        return Result.success(service.queryInstance(id));
    }

    @Operation(summary = "查询同步Execution的Attempt历史")
    @GetMapping("/instances/{id}/attempts")
    public Result<List<DataSyncAttemptVO>> attemptHistory(@PathVariable("id") String id) {
        return Result.success(service.queryAttempts(id));
    }

    @Operation(summary = "查询单表同步Attempt历史")
    @GetMapping("/instances/{id}/tables/{tableExecutionId}/attempts")
    public Result<List<DataSyncTableAttemptVO>> tableAttemptHistory(
            @PathVariable("id") String id, @PathVariable("tableExecutionId") String tableExecutionId) {
        return Result.success(service.queryTableAttempts(id, tableExecutionId));
    }

    @Operation(summary = "查询离线同步Execution Runtime Trace汇总")
    @GetMapping("/instances/{id}/trace/summary")
    public Result<DataSyncTraceSummaryVO> executionTraceSummary(
            @PathVariable("id") String id, @RequestParam(value = "attemptNo", required = false) Integer attemptNo) {
        return Result.success(service.queryExecutionTraceSummary(id, attemptNo));
    }

    @Operation(summary = "Cursor分页查询离线同步Source Split诊断")
    @GetMapping("/instances/{id}/trace/source")
    public Result<DataSyncTracePageVO<DataSyncSourceTraceVO>> executionSourceTrace(
            @PathVariable("id") String id,
            @RequestParam(value = "attemptNo", required = false) Integer attemptNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(service.queryExecutionSourceTrace(id, attemptNo, pageSize, cursor, status));
    }

    @Operation(summary = "Cursor分页查询离线同步Sink Batch诊断")
    @GetMapping("/instances/{id}/trace/sink")
    public Result<DataSyncTracePageVO<DataSyncSinkTraceVO>> executionSinkTrace(
            @PathVariable("id") String id,
            @RequestParam(value = "attemptNo", required = false) Integer attemptNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(service.queryExecutionSinkTrace(id, attemptNo, pageSize, cursor, status));
    }

    @Operation(summary = "分页查询同步实例")
    @PostMapping("/instances/page")
    public Result<PagingData<DataSyncInstanceVO>> instancePage(@Valid @RequestBody DataSyncInstanceQueryDTO dto) {
        return Result.success(service.queryInstancePage(dto));
    }

    @Operation(summary = "停止同步实例")
    @PostMapping("/instances/{id}/cancel")
    public Result<DataSyncInstanceVO> cancelInstance(@PathVariable("id") String id) {
        return Result.success(service.cancelInstance(id));
    }

}
