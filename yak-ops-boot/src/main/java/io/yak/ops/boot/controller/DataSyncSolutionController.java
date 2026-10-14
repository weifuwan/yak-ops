package io.yak.ops.boot.controller;

import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.Result;
import io.yak.ops.plugin.task.datasync.DataSyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.solution.Solution;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated type-level synchronization choices; actual connection checks happen on save and execution. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class DataSyncSolutionController {
    @Resource
    private DataSyncTaskPlugin plugin;

    /**
     * Returns implemented solutions for an ordered datasource-type pair.
     *
     * @param sourceType product source type
     * @param targetType product target type
     * @return supported solution and step combinations
     */
    @GetMapping("/solutions")
    public Result<List<Solution>> solutions(@RequestParam String sourceType, @RequestParam String targetType) {
        WorkspaceContext.requireWorkspaceId();
        try {
            return Result.success(plugin.solutions(sourceType, targetType));
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
    }
}
