package io.yak.ops.boot.controller;

import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.Result;
import io.yak.ops.plugin.task.datasync.PipelineConnectorCatalog;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Installed Connector capabilities for the authenticated Task editor. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class DataSyncConnectorController {
    @Resource
    private PipelineConnectorCatalog catalog;

    /**
     * Returns installed source and sink Connector pairs for the selected datasource types.
     *
     * @param sourceType product source type
     * @param targetType product target type
     * @return Connector-owned capabilities without connection credentials
     */
    @GetMapping("/connectors")
    public Result<List<PipelineConnectorCatalog.ConnectorPair>> connectors(
            @RequestParam String sourceType, @RequestParam String targetType) {
        WorkspaceContext.requireWorkspaceId();
        try {
            return Result.success(catalog.list(sourceType, targetType));
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
    }
}
