package io.yak.ops.api.controller;

import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableFieldDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableFieldVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionVersionHistoryVO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.application.service.DimensionModelingService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/** HTTP boundary for project-scoped dimension table and field modeling. */
@RestController
@RequestMapping("projects/{projectCode}/modeling/dimensions")
public class DimensionModelingController {

    @Resource
    private DimensionModelingService dimensionModelingService;

    @PostMapping
    public Result<DimensionTableVO> create(@RequestBody DimensionTableDTO dto) {
        return Result.success(dimensionModelingService.create(dto));
    }

    @PutMapping
    public Result<DimensionTableVO> update(@RequestBody DimensionTableDTO dto) {
        return Result.success(dimensionModelingService.updateById(dto));
    }

    @PutMapping("/{id}/submit")
    public Result<DimensionTableVO> submit(@PathVariable("id") String id) {
        return Result.success(dimensionModelingService.submit(id));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable("id") String id) {
        return Result.success(dimensionModelingService.delete(id));
    }

    @DeleteMapping("/batch")
    public Result<Boolean> deleteBatch(@RequestBody List<String> ids) {
        return Result.success(dimensionModelingService.deleteBatch(ids));
    }

    @GetMapping("/{id}")
    public Result<DimensionTableVO> get(@PathVariable("id") String id) {
        return Result.success(dimensionModelingService.selectById(id));
    }

    @PostMapping("/page")
    public PaginationResult<DimensionTableVO> page(@RequestBody DimensionTableDTO dto) {
        return dimensionModelingService.page(dto);
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

    @GetMapping("/rollback/{id}")
    public Result<DimensionTableVO> rollback(
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

    @PostMapping("/{id}/fields")
    public Result<DimensionTableFieldVO> createField(
            @PathVariable("id") String id,
            @RequestBody DimensionTableFieldDTO dto) {
        return Result.success(dimensionModelingService.createField(id, dto));
    }

    @PutMapping("/{id}/fields")
    public Result<List<DimensionTableFieldVO>> replaceFields(
            @PathVariable("id") String id,
            @RequestBody List<DimensionTableFieldDTO> fields) {
        return Result.success(dimensionModelingService.replaceFields(id, fields));
    }

    @PutMapping("/{id}/fields/{fieldId}")
    public Result<DimensionTableFieldVO> updateField(
            @PathVariable("id") String id,
            @PathVariable("fieldId") String fieldId,
            @RequestBody DimensionTableFieldDTO dto) {
        return Result.success(dimensionModelingService.updateField(id, fieldId, dto));
    }

    @DeleteMapping("/{id}/fields/{fieldId}")
    public Result<String> deleteField(
            @PathVariable("id") String id,
            @PathVariable("fieldId") String fieldId) {
        return Result.success(dimensionModelingService.deleteField(id, fieldId));
    }
}
