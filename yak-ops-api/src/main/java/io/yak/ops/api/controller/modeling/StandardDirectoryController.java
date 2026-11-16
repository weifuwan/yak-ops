package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.standarddirectory.StandardDirectoryDTO;
import io.yak.ops.modeling.api.vo.standarddirectory.StandardDirectoryVO;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;
import io.yak.ops.modeling.application.service.StandardDirectoryService;

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

/** HTTP API for shared data-standard directories. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/standard-directories")
public class StandardDirectoryController extends BaseController {

    @Resource
    private StandardDirectoryService standardDirectoryService;

    @PostMapping("/page")
    public Result<PageInfo<StandardDirectoryVO>> page(@RequestBody StandardDirectoryDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(standardDirectoryService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<StandardDirectoryVO>> list(@ModelAttribute StandardDirectoryDTO query) {
        return Result.success(standardDirectoryService.list(query));
    }

    @GetMapping("/tree")
    public Result<List<ModelingAssetTreeVO>> tree(@RequestParam("module") String module) {
        return Result.success(standardDirectoryService.tree(module));
    }

    @GetMapping("/{id}")
    public Result<StandardDirectoryVO> getById(@PathVariable("id") String id) {
        return Result.success(standardDirectoryService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody StandardDirectoryDTO dto) {
        return Result.success(standardDirectoryService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<StandardDirectoryVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody StandardDirectoryDTO dto) {
        return Result.success(standardDirectoryService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(standardDirectoryService.delete(id));
    }
}
