package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.application.service.DwLayerService;

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
@RequestMapping("/projects/{projectCode}/modeling/dw-layer")
public class DwLayerController extends BaseController {

    @Resource
    private DwLayerService dwLayerService;

    /**
     * 新增数仓分层
     */
    @Operation(description = "Create warehouse layer")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    @PostMapping
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody DwLayerDTO dto) {
        return Result.success(dwLayerService.create(dto));
    }

    /**
     * 修改数仓分层
     */
    @Operation(description = "Update warehouse layer")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    @PutMapping
    public Result<DwLayerVO> update(
                                    @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                    @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                    @RequestBody DwLayerDTO dto) {
        return Result.success(dwLayerService.updateById(dto));
    }

    /**
     * 删除数仓分层
     */
    @Operation(description = "Delete warehouse layer")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    @DeleteMapping("/{id}")
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable String id) {
        return Result.success(dwLayerService.delete(id));
    }

    /**
     * 根据ID查询数仓分层
     */
    @GetMapping("/{id}")
    public Result<DwLayerVO> selectById(@PathVariable String id) {
        return Result.success(dwLayerService.selectById(id));
    }

    /**
     * 分页查询数仓分层
     */
    @PostMapping(value = "page")
    public Result<PageInfo<DwLayerVO>> paging(@RequestBody DwLayerDTO dto) {
        checkPageParams(DwLayerDTO.getPageNo(), DwLayerDTO.getPageSize());
        return Result.success(PageInfo.of(dwLayerService.paging(dto)));
    }

    /**
     * 分页查询数仓分层
     */
    @GetMapping(value = "all")
    public Result<List<DwLayerVO>> all(DwLayerDTO dto) {
        return Result.success(dwLayerService.all(dto));
    }

    /**
     * 数仓分层选项
     */
    @PostMapping(value = "option")
    public Result<List<OptionVO>> option(@RequestBody List<String> dwType) {
        return Result.success(dwLayerService.option(dwType));
    }

    /**
     * 通过表获取数仓分层选项
     */
    @GetMapping(value = "option/modal-type")
    public Result<List<OptionVO>> optionByModalType(@RequestParam("modalType") DwModelEnum modalType) {
        return Result.success(dwLayerService.optionByModalType(modalType));
    }
}
