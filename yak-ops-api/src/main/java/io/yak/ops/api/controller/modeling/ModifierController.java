package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.modifier.ModifierDTO;
import io.yak.ops.modeling.api.vo.ModelingAssetVersionVO;
import io.yak.ops.modeling.api.vo.modifier.ModifierVO;
import io.yak.ops.modeling.application.service.ModifierService;

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

/** HTTP API for Modifier. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/modifiers")
public class ModifierController extends BaseController {

    @Resource
    private ModifierService modifierService;

    @PostMapping("/page")
    public Result<PageInfo<ModifierVO>> page(@RequestBody ModifierDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(modifierService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<ModifierVO>> list(@ModelAttribute ModifierDTO query) {
        return Result.success(modifierService.list(query));
    }

    @GetMapping("/{id}")
    public Result<ModifierVO> getById(@PathVariable("id") String id) {
        return Result.success(modifierService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody ModifierDTO dto) {
        return Result.success(modifierService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<ModifierVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody ModifierDTO dto) {
        return Result.success(modifierService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(modifierService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<ModifierVO> submit(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(modifierService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(modifierService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<ModifierVO> rollback(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(modifierService.rollback(id, versionNumber, loginUser.getUserName()));
    }

}
