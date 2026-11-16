package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodVO;
import io.yak.ops.modeling.application.service.TimePeriodService;
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
import io.yak.ops.modeling.api.vo.ModelingAssetVersionVO;
import io.yak.ops.modeling.api.dto.timeperiod.TimePeriodCategoryDTO;
import io.yak.ops.modeling.api.vo.timeperiod.TimePeriodCategoryVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

/** HTTP API for TimePeriod. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/time-periods")
public class TimePeriodController extends BaseController {

    @Resource
    private TimePeriodService timePeriodService;

    @PostMapping("/page")
    public Result<PageInfo<TimePeriodVO>> page(@RequestBody TimePeriodDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(timePeriodService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<TimePeriodVO>> list(@ModelAttribute TimePeriodDTO query) {
        return Result.success(timePeriodService.list(query));
    }

    @GetMapping("/{id}")
    public Result<TimePeriodVO> getById(@PathVariable("id") String id) {
        return Result.success(timePeriodService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody TimePeriodDTO dto) {
        return Result.success(timePeriodService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<TimePeriodVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody TimePeriodDTO dto) {
        return Result.success(timePeriodService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(timePeriodService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<TimePeriodVO> submit(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(timePeriodService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(timePeriodService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<TimePeriodVO> rollback(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(timePeriodService.rollback(id, versionNumber, loginUser.getUserName()));
    }


    @PostMapping("/categories/page")
    public Result<PageInfo<TimePeriodCategoryVO>> pageCategories(@RequestBody TimePeriodCategoryDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(timePeriodService.pageCategories(query)));
    }

    @GetMapping("/categories/list")
    public Result<List<TimePeriodCategoryVO>> listCategories(@ModelAttribute TimePeriodCategoryDTO query) {
        return Result.success(timePeriodService.listCategories(query));
    }

    @GetMapping("/categories/{categoryId}")
    public Result<TimePeriodCategoryVO> getCategoryById(@PathVariable("categoryId") String categoryId) {
        return Result.success(timePeriodService.getCategoryById(categoryId));
    }

    @PostMapping("/categories")
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> createCategory(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody TimePeriodCategoryDTO dto) {
        return Result.success(timePeriodService.createCategory(dto, loginUser.getUserName()));
    }

    @PutMapping("/categories/{categoryId}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<TimePeriodCategoryVO> updateCategory(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("categoryId") String categoryId,
            @RequestBody TimePeriodCategoryDTO dto) {
        return Result.success(timePeriodService.updateCategory(categoryId, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/categories/{categoryId}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> deleteCategory(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("categoryId") String categoryId) {
        return Result.success(timePeriodService.deleteCategory(categoryId));
    }

}
