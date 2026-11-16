package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.compositeindicator.CompositeIndicatorDTO;
import io.yak.ops.modeling.api.vo.compositeindicator.CompositeIndicatorVO;
import io.yak.ops.modeling.api.vo.ModelingAssetVersionVO;
import io.yak.ops.modeling.application.service.CompositeIndicatorService;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for CompositeIndicator. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/composite-indicators")
public class CompositeIndicatorController extends BaseController {

    @Resource
    private CompositeIndicatorService compositeIndicatorService;

    @PostMapping("/page")
    public Result<PageInfo<CompositeIndicatorVO>> page(@RequestBody CompositeIndicatorDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(compositeIndicatorService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<CompositeIndicatorVO>> list(@ModelAttribute CompositeIndicatorDTO query) {
        return Result.success(compositeIndicatorService.list(query));
    }

    @GetMapping("/{id}")
    public Result<CompositeIndicatorVO> getById(@PathVariable("id") String id) {
        return Result.success(compositeIndicatorService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody CompositeIndicatorDTO dto) {
        return Result.success(compositeIndicatorService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<CompositeIndicatorVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody CompositeIndicatorDTO dto) {
        return Result.success(compositeIndicatorService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(compositeIndicatorService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<CompositeIndicatorVO> submit(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(compositeIndicatorService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(compositeIndicatorService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<CompositeIndicatorVO> rollback(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(compositeIndicatorService.rollback(id, versionNumber, loginUser.getUserName()));
    }

}
