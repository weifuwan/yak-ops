package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.MetricsService;
import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATA_SYNC专属的聚合仪表盘入口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "数据同步指标")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class MetricsController {

    @Resource
    private MetricsService service;

    @Operation(summary = "查询运维中心数据同步聚合指标")
    @PostMapping("/operations/dashboard")
    public Result<DataSyncOperationsDashboardVO> operationsDashboard(
            @Valid @RequestBody DataSyncOperationsDashboardDTO dto) {
        return Result.success(service.queryOperationsDashboard(dto));
    }
}
