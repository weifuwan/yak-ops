package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.businesstype.BusinessTypeDTO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.application.service.BusinessTypeService;

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
@RequestMapping("/projects/{projectCode}/modeling/business-type")
public class BusinessTypeController extends BaseController {

    @Resource
    private BusinessTypeService businessTypeService;

    /**
     * 新增业务分类
     */
    @Operation(description = "Create business type")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody BusinessTypeDTO businessType) {
        return Result.success(businessTypeService.create(businessType));
    }

    /**
     * 修改业务分类
     */
    @Operation(description = "Update business type")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<BusinessTypeVO> update(
                                         @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                         @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                         @RequestBody BusinessTypeDTO businessType) {
        return Result.success(businessTypeService.updateById(businessType));
    }

    /**
     * 删除业务分类
     */
    @Operation(description = "Delete business type")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable String id) {
        return Result.success(businessTypeService.delete(id));
    }

    /**
     * 根据ID查询业务分类
     */
    @GetMapping("/{id}")
    public Result<BusinessTypeVO> selectById(@PathVariable String id) {
        return Result.success(businessTypeService.selectById(id));
    }

    /**
     * 分页查询业务分类
     */
    @PostMapping(value = "page")
    public Result<PageInfo<BusinessTypeVO>> paging(@RequestBody BusinessTypeDTO businessTypeDTO) {
        checkPageParams(businessTypeDTO.getPageNo(), businessTypeDTO.getPageSize());
        return Result.success(PageInfo.of(businessTypeService.paging(businessTypeDTO)));
    }

    /**
     * 树形结构查询业务分类
     */
    @GetMapping(value = "tree")
    public Result<List<TreeVO>> tree(@RequestParam("keyword") String keyword) {
        return Result.success(businessTypeService.tree(keyword, null));
    }

    /**
     * 查询业务分类所有数据
     */
    @GetMapping(value = "all")
    public Result<List<BusinessTypeVO>> all() {
        return Result.success(businessTypeService.all());
    }

    /**
     * 根据业务分类，查询对应的数据域
     */
    @GetMapping(value = "/select/related-domain/{id}")
    public Result<List<DataDomainVO>> selectTypeData(@PathVariable("id") String id) {
        return Result.success(businessTypeService.selectTypeData(id));
    }

    /**
     * 更新业务分类 - 数据域关系
     */
    @Operation(description = "Update business type data-domain relations")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping(value = "/update/related-domain/{id}")
    public Result<Boolean> updateTypeData(
                                          @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                          @PathVariable("id") String id, @RequestBody List<String> dataDomainIds) {
        return Result.success(businessTypeService.updateTypeData(id, dataDomainIds));
    }

    /**
     * 删除业务分类 - 数据域关系
     */
    @Operation(description = "Delete business type data-domain relation")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping(value = "/delete/related-domain/{id}")
    public Result<Boolean> deleteTypeData(
                                          @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                          @PathVariable("id") String id,
            @RequestParam("dataDomainId") String dataDomainId) {
        return Result.success(businessTypeService.deleteTypeData(id, dataDomainId));
    }

    /**
     * 获取业务分类 - 数据域选项
     */
    @GetMapping(value = "/option/related-domain/{id}")
    public Result<List<OptionVO>> optionRelatedDomain(
            @PathVariable("id") String id,
            @RequestParam("checked") boolean checked) {
        return Result.success(businessTypeService.optionRelatedDomain(id, checked));
    }

    /**
     * 加载所有数据域
     */
    @Operation(description = "Load all data domains for business type")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping(value = "/loadall/related-domain/{id}")
    public Result<Boolean> onLoadAllDomains(
                                            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                            @PathVariable("id") String id) {
        return Result.success(businessTypeService.onLoadAllDomains(id));
    }
}
