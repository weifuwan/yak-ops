package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.subjectdomain.SubjectDomainDTO;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.api.vo.tree.TreeTypeValueVO;
import io.yak.ops.modeling.application.service.SubjectDomainService;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/projects/{projectCode}/modeling/subject-domain")
public class SubjectDomainController extends BaseController {

    @Resource
    private SubjectDomainService subjectDomainService;

    /**
     * 新增主题域
     */
    @Operation(description = "Create subject domain")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody SubjectDomainDTO dto) {
        return Result.success(subjectDomainService.create(dto, loginUser.getUserName()));
    }

    /**
     * 修改主题域
     */
    @Operation(description = "Update subject domain")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<SubjectDomainVO> update(
                                          @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                          @RequestBody SubjectDomainDTO dto) {
        return Result.success(subjectDomainService.updateById(dto, loginUser.getUserName()));
    }

    /**
     * 删除主题域
     */
    @Operation(description = "Delete subject domain")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable String id) {
        return Result.success(subjectDomainService.delete(id));
    }

    /**
     * 根据ID查询主题域
     */
    @GetMapping("/{id}")
    public Result<SubjectDomainVO> selectById(@PathVariable String id) {
        return Result.success(subjectDomainService.selectById(id));
    }

    /**
     * 分页查询主题域
     */
    @PostMapping(value = "page")
    public Result<PageInfo<SubjectDomainVO>> paging(@RequestBody SubjectDomainDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(subjectDomainService.paging(dto)));
    }

    /**
     * 树形结构查询主题域
     */
    @GetMapping(value = "tree")
    public Result<List<TreeTypeValueVO>> tree() {
        return Result.success(subjectDomainService.tree());
    }

    /**
     * 查询所有主题域数据
     */
    @PostMapping(value = "all")
    public Result<List<SubjectDomainVO>> all() {
        return Result.success(subjectDomainService.all());
    }
}
