package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncLogService;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATA_SYNC产品生命周期事件，现有logs路径保持向后兼容。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "数据同步日志")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class LogController {

    @Resource
    private DataSyncLogService service;

    @Operation(summary = "查询同步Execution产品事件")
    @GetMapping("/instances/{id}/logs")
    public Result<List<DataSyncExecutionEventVO>> executionLogs(@PathVariable("id") String id) {
        return Result.success(service.queryExecutionEvents(id));
    }

}
