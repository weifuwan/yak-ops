package io.yak.ops.boot.controller;

import io.yak.ops.business.task.metrics.DataSyncOperationsService;
import io.yak.ops.common.bean.vo.task.DataSyncOperationsVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Workspace-scoped Data Sync operational statistics; execution logs belong to Task Detail. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync/operations")
public class DataSyncOperationsController {

    @Resource
    private DataSyncOperationsService service;

    /**
     * Provides period metrics and a separate all-time running snapshot.
     *
     * @param syncType OFFLINE or REALTIME
     * @param range TODAY, LAST_7_DAYS or LAST_30_DAYS
     * @return Task Instance operations dashboard
     */
    @GetMapping("/dashboard")
    public Result<DataSyncOperationsVO> dashboard(
            @RequestParam String syncType, @RequestParam(defaultValue = "LAST_7_DAYS") String range) {
        return Result.success(service.dashboard(WorkspaceContext.requireWorkspaceId(), syncType, range));
    }
}
