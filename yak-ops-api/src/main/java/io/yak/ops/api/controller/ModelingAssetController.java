package io.yak.ops.api.controller;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;
import io.yak.ops.modeling.application.service.ModelingAssetService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

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

    @PostMapping("/assets/{resource}")
    public Result<String> create(
            @PathVariable("resource") String resource,
            @RequestBody Map<String, Object> payload) {
        return Result.success(modelingAssetService.create(resource, payload));
    }

    @PutMapping("/assets/{resource}")
    public Result<Map<String, Object>> update(
            @PathVariable("resource") String resource,
            @RequestBody Map<String, Object> payload) {
        return Result.success(modelingAssetService.update(resource, payload));
    }

    @DeleteMapping("/assets/{resource}/{id}")
    public Result<String> delete(
            @PathVariable("resource") String resource,
            @PathVariable("id") String id) {
        return Result.success(modelingAssetService.delete(resource, id));
    }

    @GetMapping("/assets/standard-directory/tree")
    public Result<List<ModelingAssetTreeVO>> directoryTree(@RequestParam("module") String module) {
        return Result.success(modelingAssetService.directoryTree(module));
    }

    @PostMapping("/assets/{resource}/{id}/submit")
    public Result<Map<String, Object>> submit(
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

    @PostMapping("/assets/{resource}/{id}/rollback/{versionNumber}")
    public Result<Map<String, Object>> rollback(
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
