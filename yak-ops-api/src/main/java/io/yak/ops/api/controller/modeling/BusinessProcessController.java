package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;
import io.yak.ops.modeling.application.service.BusinessProcessService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/projects/{projectCode}/modeling/business-process")
public class BusinessProcessController extends BaseController {

    @Resource
    private BusinessProcessService businessProcessService;

    /**
     * 新增业务过程
     */
    @Operation(description = "Create business process")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody BusinessProcessDTO dto) {
        return Result.success(businessProcessService.create(dto, loginUser.getUserName()));
    }

    /**
     * 修改业务过程
     */
    @Operation(description = "Update business process")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<BusinessProcessVO> update(
                                            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                            @RequestBody BusinessProcessDTO dto) {
        return Result.success(businessProcessService.updateById(dto, loginUser.getUserName()));
    }

    /**
     * 删除业务过程
     */
    @Operation(description = "Delete business process")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable String id) {
        return Result.success(businessProcessService.delete(id));
    }

    /**
     * 根据ID查询业务过程
     */
    @GetMapping("/{id}")
    public Result<BusinessProcessVO> selectById(@PathVariable String id) {
        return Result.success(businessProcessService.selectById(id));
    }

    /**
     * 分页查询业务过程
     */
    @PostMapping(value = "page")
    public Result<PageInfo<BusinessProcessVO>> paging(@RequestBody BusinessProcessDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(businessProcessService.paging(dto)));
    }

    /**
     * 分页查询业务过程
     */
    @GetMapping(value = "all")
    public Result<List<BusinessProcessVO>> all(BusinessProcessDTO dto) {
        return Result.success(businessProcessService.all(dto));
    }

    /**
     * 根据业务分类ID获取业务过程选择树
     */
    @GetMapping("/option/cascader")
    public Result<List<CascaderTreeVO>> getTreeOption(@RequestParam(name = "busTypeId", required = false) String busTypeId) {
        return Result.success(businessProcessService.treeList(busTypeId));
    }
}
