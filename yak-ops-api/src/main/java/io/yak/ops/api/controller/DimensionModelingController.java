package io.yak.ops.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableFieldDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableFieldVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionVersionHistoryVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.application.service.DimensionModelingService;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.*;

/** HTTP boundary for project-scoped dimension table and field modeling. */
@RestController
@RequestMapping("projects/{projectCode}/modeling/dimensions")
public class DimensionModelingController extends BaseController {

    @Resource
    private DimensionModelingService dimensionModelingService;

    @Operation(description = "Create dimension table")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<DimensionTableVO> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody DimensionTableDTO dto) {
        return Result.success(dimensionModelingService.create(dto));
    }

    @Operation(description = "Update dimension table")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<DimensionTableVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody DimensionTableDTO dto) {
        return Result.success(dimensionModelingService.updateById(dto));
    }

    @Operation(description = "Submit dimension table")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping("/{id}/submit")
    public Result<DimensionTableVO> submit(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(dimensionModelingService.submit(id));
    }

    @Operation(description = "Delete dimension table")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(dimensionModelingService.delete(id));
    }

    @Operation(description = "Delete dimension tables in batch")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/batch")
    public Result<Boolean> deleteBatch(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody List<String> ids) {
        return Result.success(dimensionModelingService.deleteBatch(ids));
    }

    @GetMapping("/{id}")
    public Result<DimensionTableVO> get(@PathVariable("id") String id) {
        return Result.success(dimensionModelingService.selectById(id));
    }

    @PostMapping("/page")
    public Result<PageInfo<DimensionTableVO>> page(@RequestBody DimensionTableDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(dimensionModelingService.page(dto)));
    }

    @PostMapping("/tree")
    public Result<List<DimensionTreeNodeVO>> tree(@RequestBody DimensionTableDTO dto) {
        return Result.success(dimensionModelingService.tree(dto));
    }

    @PostMapping("/all")
    public Result<List<DimensionTableVO>> all(@RequestBody(required = false) DimensionTableDTO dto) {
        return Result.success(dimensionModelingService.all(dto));
    }

    @GetMapping("/version-history/{id}")
    public Result<List<DimensionVersionHistoryVO>> versionHistory(@PathVariable("id") String id) {
        return Result.success(dimensionModelingService.getVersionHistory(id));
    }

    @Operation(description = "Rollback dimension table version")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PostMapping("/rollback/{id}")
    public Result<DimensionTableVO> rollback(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestParam("targetVersion") Integer targetVersion) {
        return Result.success(dimensionModelingService.rollbackToVersion(id, targetVersion));
    }

    @GetMapping("/options/select")
    public Result<List<OptionVO>> selectOptions() {
        return Result.success(dimensionModelingService.getSelectOption());
    }

    @GetMapping("/options/select/dim")
    public Result<List<OptionVO>> dimensionOptions() {
        return Result.success(dimensionModelingService.getDimOption());
    }

    @GetMapping("/{id}/fields")
    public Result<List<DimensionTableFieldVO>> fields(@PathVariable("id") String id) {
        return Result.success(dimensionModelingService.listFields(id));
    }

    @Operation(description = "Create dimension table field")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping("/{id}/fields")
    public Result<DimensionTableFieldVO> createField(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody DimensionTableFieldDTO dto) {
        return Result.success(dimensionModelingService.createField(id, dto));
    }

    @Operation(description = "Replace dimension table fields")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping("/{id}/fields")
    public Result<List<DimensionTableFieldVO>> replaceFields(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody List<DimensionTableFieldDTO> fields) {
        return Result.success(dimensionModelingService.replaceFields(id, fields));
    }

    @Operation(description = "Update dimension table field")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping("/{id}/fields/{fieldId}")
    public Result<DimensionTableFieldVO> updateField(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @PathVariable("fieldId") String fieldId,
            @RequestBody DimensionTableFieldDTO dto) {
        return Result.success(dimensionModelingService.updateField(id, fieldId, dto));
    }

    @Operation(description = "Delete dimension table field")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}/fields/{fieldId}")
    public Result<String> deleteField(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @PathVariable("fieldId") String fieldId) {
        return Result.success(dimensionModelingService.deleteField(id, fieldId));
    }
}
