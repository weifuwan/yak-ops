package io.yak.ops.api.controller.modeling;

/** HTTP API for DerivedIndicator. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/derived-indicators")
public class DerivedIndicatorController extends BaseController {

    @Resource
    private DerivedIndicatorService derivedIndicatorService;

    @PostMapping("/page")
    public Result<PageInfo<DerivedIndicatorVO>> page(@RequestBody DerivedIndicatorDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(derivedIndicatorService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<DerivedIndicatorVO>> list(@ModelAttribute DerivedIndicatorDTO query) {
        return Result.success(derivedIndicatorService.list(query));
    }

    @GetMapping("/{id}")
    public Result<DerivedIndicatorVO> getById(@PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody DerivedIndicatorDTO dto) {
        return Result.success(derivedIndicatorService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<DerivedIndicatorVO> update(
                                             @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                             @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                             @PathVariable("id") String id,
                                             @RequestBody DerivedIndicatorDTO dto) {
        return Result.success(derivedIndicatorService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<DerivedIndicatorVO> submit(
                                             @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                             @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                             @PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(derivedIndicatorService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<DerivedIndicatorVO> rollback(
                                               @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                               @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                               @PathVariable("id") String id,
                                               @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(derivedIndicatorService.rollback(id, versionNumber, loginUser.getUserName()));
    }

}
