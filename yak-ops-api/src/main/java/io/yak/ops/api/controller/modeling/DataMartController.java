package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.application.service.DataMartService;

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
@RequestMapping("/projects/{projectCode}/modeling/data-mart")
public class DataMartController extends BaseController {

    @Resource
    private DataMartService dataMartService;

    /**
     * 新增数据集市
     */
    @Operation(description = "Create data mart")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody DataMartDTO dto) {
        return Result.success(dataMartService.create(dto));
    }

    /**
     * 修改数据集市
     */
    @Operation(description = "Update data mart")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<DataMartVO> update(
                                     @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                     @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                     @RequestBody DataMartDTO dto) {
        return Result.success(DataMartService.updateById(dto));
    }

    /**
     * 删除数据集市
     */
    @Operation(description = "Delete data mart")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable String id) {
        return Result.success(DataMartService.delete(id));
    }

    /**
     * 根据ID查询数据集市
     */
    @GetMapping("/{id}")
    public Result<DataMartVO> selectById(@PathVariable String id) {
        return Result.success(DataMartService.selectById(id));
    }

    /**
     * 分页查询数据集市
     */
    @PostMapping(value = "page")
    public Result<PageInfo<DataMartVO>> paging(@RequestBody DataMartDTO dto) {
        checkPageParams(dto.getPageNo(), dto.getPageSize());
        return Result.success(PageInfo.of(DataMartService.paging(dto)));
    }

    /**
     * 树形结构查询数据集市
     */
    @GetMapping(value = "tree")
    public Result<List<TreeVO>> tree(@RequestParam("keyword") String keyword) {
        return Result.success(DataMartService.tree(keyword));
    }

    /**
     * 查询所有数据集市数据
     */
    @GetMapping(value = "all")
    public Result<List<DataMartVO>> all() {
        return Result.success(DataMartService.all());
    }
}
