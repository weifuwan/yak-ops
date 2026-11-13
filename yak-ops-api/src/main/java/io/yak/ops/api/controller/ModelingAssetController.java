package io.yak.ops.api.controller;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;
import io.yak.ops.modeling.application.service.ModelingAssetService;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

/** HTTP boundary for project-scoped data standards and indicator assets. */
@RestController
@RequestMapping("projects/{projectCode}/modeling")
public class ModelingAssetController extends BaseController {

    @Resource
    private ModelingAssetService modelingAssetService;

    @PostMapping("/assets/{resource}/page")
    public Result<PageInfo<Map<String, Object>>> page(
                                                      @PathVariable("resource") String resource,
                                                      @RequestBody Map<String, Object> params) {
        checkPageParams(parsePageParam(params, "pageNo", 1), parsePageParam(params, "pageSize", 10));
        return Result.success(PageInfo.of(modelingAssetService.page(resource, params)));
    }

    @GetMapping("/assets/{resource}/all")
    public Result<List<Map<String, Object>>> all(
                                                 @PathVariable("resource") String resource,
                                                 @RequestParam Map<String, Object> params) {
        return Result.success(modelingAssetService.all(resource, params));
    }

    @GetMapping("/assets/{resource}/{id}")
    public Result<Map<String, Object>> get(
                                           @PathVariable("resource") String resource,
                                           @PathVariable("id") String id) {
        return Result.success(modelingAssetService.get(resource, id));
    }

    @Operation(description = "Create modeling asset")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping("/assets/{resource}")
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("resource") String resource,
                                 @RequestBody Map<String, Object> payload) {
        return Result.success(modelingAssetService.create(resource, payload));
    }

    @Operation(description = "Update modeling asset")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping("/assets/{resource}")
    public Result<Map<String, Object>> update(
                                              @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                              @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                              @PathVariable("resource") String resource,
                                              @RequestBody Map<String, Object> payload) {
        return Result.success(modelingAssetService.update(resource, payload));
    }

    @Operation(description = "Delete modeling asset")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/assets/{resource}/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("resource") String resource,
                                 @PathVariable("id") String id) {
        return Result.success(modelingAssetService.delete(resource, id));
    }

    @GetMapping("/assets/standard-directory/tree")
    public Result<List<ModelingAssetTreeVO>> directoryTree(@RequestParam("module") String module) {
        return Result.success(modelingAssetService.directoryTree(module));
    }

    @Operation(description = "Submit modeling asset")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PostMapping("/assets/{resource}/{id}/submit")
    public Result<Map<String, Object>> submit(
                                              @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                              @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                              @PathVariable("resource") String resource,
                                              @PathVariable("id") String id) {
        return Result.success(modelingAssetService.submit(resource, id));
    }

    @GetMapping("/assets/{resource}/{id}/versions")
    public Result<List<Map<String, Object>>> versions(
                                                      @PathVariable("resource") String resource,
                                                      @PathVariable("id") String id) {
        return Result.success(modelingAssetService.versions(resource, id));
    }

    @Operation(description = "Rollback modeling asset version")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PostMapping("/assets/{resource}/{id}/rollback/{versionNumber}")
    public Result<Map<String, Object>> rollback(
                                                @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                                @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                                @PathVariable("resource") String resource,
                                                @PathVariable("id") String id,
                                                @PathVariable("versionNumber") Integer versionNumber) {
        return Result.success(modelingAssetService.rollback(resource, id, versionNumber));
    }
    private int parsePageParam(Map<String, Object> params, String name, int defaultValue) {
        Object value = params.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }

}
