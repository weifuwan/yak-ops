package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.application.service.DataDomainService;

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
@RequestMapping("/projects/{projectCode}/modeling/data-domain")
public class DataDomainController extends BaseController {

    @Resource
    private DataDomainService dataDomainService;

    @Operation(description = "Create data domain")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody DataDomainDTO dto) {
        return Result.success(dataDomainService.create(dto, loginUser.getUserName()));
    }

    @Operation(description = "Update data domain")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<DataDomainVO> update(
                                       @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                       @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                       @RequestBody DataDomainDTO dto) {
        return Result.success(dataDomainService.updateById(dto, loginUser.getUserName()));
    }

    @Operation(description = "Delete data domain")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable String id) {
        return Result.success(dataDomainService.delete(id));
    }

    @GetMapping("/{id}")
    public Result<DataDomainVO> selectById(@PathVariable String id) {
        return Result.success(dataDomainService.selectById(id));
    }

    @PostMapping(value = "page")
    public Result<PageInfo<DataDomainVO>> paging(@RequestBody DataDomainDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(dataDomainService.paging(dto)));
    }

    @GetMapping(value = "all")
    public Result<List<DataDomainVO>> all() {
        return Result.success(dataDomainService.all());
    }

}
