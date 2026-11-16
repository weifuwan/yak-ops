package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.derivedindicator.DerivedIndicatorDTO;
import io.yak.ops.modeling.api.vo.ModelingAssetVersionVO;
import io.yak.ops.modeling.api.vo.derivedindicator.DerivedIndicatorVO;
import io.yak.ops.modeling.application.service.DerivedIndicatorService;

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

import io.swagger.v3.oas.annotations.Parameter;

/** HTTP API for DerivedIndicator. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/derived-indicators")
public class DerivedIndicatorController extends BaseController {

    @Resource
    private DerivedIndicatorService derivedIndicatorService;

    @PostMapping("/page")
    public Result<PageInfo<DerivedIndicatorVO>> page(@RequestBody DerivedIndicatorDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(derivedIndicatorService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<DerivedIndicatorVO>> list(@ModelAttribute DerivedIndicatorDTO query) {
        return Result.success(derivedIndicatorService.list(query));
    }

    @GetMapping("/{id}")
    public Result<DerivedIndicatorVO> getById(@PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody DerivedIndicatorDTO dto) {
        return Result.success(derivedIndicatorService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<DerivedIndicatorVO> update(
                                             @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                             @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                             @PathVariable("id") String id,
                                             @RequestBody DerivedIndicatorDTO dto) {
        return Result.success(derivedIndicatorService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<DerivedIndicatorVO> submit(
                                             @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                             @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                             @PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<DerivedIndicatorVO> rollback(
                                               @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                               @PathVariable("id") String id,
                                               @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(derivedIndicatorService.rollback(id, versionNumber, loginUser.getUserName()));
    }

}
