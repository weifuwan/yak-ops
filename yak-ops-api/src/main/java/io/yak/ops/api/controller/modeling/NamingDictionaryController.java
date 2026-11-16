package io.yak.ops.api.controller.modeling;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;
import io.yak.ops.api.controller.BaseController;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.modeling.api.dto.namingdictionary.NamingDictionaryDTO;
import io.yak.ops.modeling.api.vo.namingdictionary.NamingDictionaryVO;
import io.yak.ops.modeling.application.service.NamingDictionaryService;
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

/** HTTP API for NamingDictionary. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/naming-dictionaries")
public class NamingDictionaryController extends BaseController {

    @Resource
    private NamingDictionaryService namingDictionaryService;

    @PostMapping("/page")
    public Result<PageInfo<NamingDictionaryVO>> page(@RequestBody NamingDictionaryDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(namingDictionaryService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<NamingDictionaryVO>> list(@ModelAttribute NamingDictionaryDTO query) {
        return Result.success(namingDictionaryService.list(query));
    }

    @GetMapping("/{id}")
    public Result<NamingDictionaryVO> getById(@PathVariable("id") String id) {
        return Result.success(namingDictionaryService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @RequestBody NamingDictionaryDTO dto) {
        return Result.success(namingDictionaryService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<NamingDictionaryVO> update(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id,
            @RequestBody NamingDictionaryDTO dto) {
        return Result.success(namingDictionaryService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
            @PathVariable("id") String id) {
        return Result.success(namingDictionaryService.delete(id));
    }


}
