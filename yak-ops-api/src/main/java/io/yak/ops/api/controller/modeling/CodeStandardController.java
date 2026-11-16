package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.codestandard.CodeDetailDTO;
import io.yak.ops.modeling.api.dto.codestandard.CodeStandardDTO;
import io.yak.ops.modeling.api.vo.codestandard.CodeDetailVO;
import io.yak.ops.modeling.api.vo.codestandard.CodeStandardVO;
import io.yak.ops.modeling.application.service.CodeStandardService;

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
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for standard codes and their nested code details. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/code-standards")
public class CodeStandardController extends BaseController {

    @Resource
    private CodeStandardService codeStandardService;

    @PostMapping("/page")
    public Result<PageInfo<CodeStandardVO>> page(@RequestBody CodeStandardDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(codeStandardService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<CodeStandardVO>> list(@ModelAttribute CodeStandardDTO query) {
        return Result.success(codeStandardService.list(query));
    }

    @GetMapping("/{id}")
    public Result<CodeStandardVO> getById(@PathVariable("id") String id) {
        return Result.success(codeStandardService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody CodeStandardDTO dto) {
        return Result.success(codeStandardService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<CodeStandardVO> update(
                                         @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                         @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                         @PathVariable("id") String id,
                                         @RequestBody CodeStandardDTO dto) {
        return Result.success(codeStandardService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("id") String id) {
        return Result.success(codeStandardService.delete(id));
    }

    @GetMapping("/{id}/details")
    public Result<List<CodeDetailVO>> listDetails(@PathVariable("id") String id) {
        return Result.success(codeStandardService.listDetails(id));
    }

    @PostMapping("/{id}/details")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> createDetail(
                                       @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                       @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                       @PathVariable("id") String id,
                                       @RequestBody CodeDetailDTO dto) {
        return Result.success(codeStandardService.createDetail(id, dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}/details/{detailId}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<CodeDetailVO> updateDetail(
                                             @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                             @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                             @PathVariable("id") String id,
                                             @PathVariable("detailId") String detailId,
                                             @RequestBody CodeDetailDTO dto) {
        return Result.success(codeStandardService.updateDetail(id, detailId, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> deleteDetail(
                                       @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                       @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                       @PathVariable("id") String id,
                                       @PathVariable("detailId") String detailId) {
        return Result.success(codeStandardService.deleteDetail(id, detailId));
    }
}
