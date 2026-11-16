package io.yak.ops.api.controller.modeling;

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
