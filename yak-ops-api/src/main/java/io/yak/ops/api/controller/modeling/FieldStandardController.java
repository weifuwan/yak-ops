package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.fieldstandard.FieldStandardDTO;
import io.yak.ops.modeling.api.vo.fieldstandard.FieldStandardVO;
import io.yak.ops.modeling.application.service.FieldStandardService;
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

/** HTTP API for FieldStandard. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/field-standards")
public class FieldStandardController extends BaseController {

    @Resource
    private FieldStandardService fieldStandardService;

    @PostMapping("/page")
    public Result<PageInfo<FieldStandardVO>> page(@RequestBody FieldStandardDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(fieldStandardService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<FieldStandardVO>> list(@ModelAttribute FieldStandardDTO query) {
        return Result.success(fieldStandardService.list(query));
    }

    @GetMapping("/{id}")
    public Result<FieldStandardVO> getById(@PathVariable("id") String id) {
        return Result.success(fieldStandardService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody FieldStandardDTO dto) {
        return Result.success(fieldStandardService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<FieldStandardVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody FieldStandardDTO dto) {
        return Result.success(fieldStandardService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(fieldStandardService.delete(id));
    }


}
