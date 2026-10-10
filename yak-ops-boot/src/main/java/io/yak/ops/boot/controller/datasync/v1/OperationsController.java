package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncOperationsService;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DATA_SYNC运维任务分页读模型；不拥有指标聚合。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "数据同步运维")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class OperationsController {

    @Resource
    private DataSyncOperationsService service;

    @Operation(summary = "分页查询运维中心数据同步任务运行态")
    @PostMapping("/operations/tasks/page")
    public Result<PagingData<DataSyncTaskOperationVO>> taskOperationPage(@Valid @RequestBody DataSyncTaskQueryDTO dto) {
        return Result.success(service.queryTaskOperationPage(dto));
    }

}
