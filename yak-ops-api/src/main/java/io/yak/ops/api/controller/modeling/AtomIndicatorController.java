package io.yak.ops.api.controller.modeling;

/** HTTP API for AtomIndicator. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/atom-indicators")
public class AtomIndicatorController extends BaseController {

    @Resource
    private AtomIndicatorService atomIndicatorService;

    @PostMapping("/page")
    public Result<PageInfo<AtomIndicatorVO>> page(@RequestBody AtomIndicatorDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(atomIndicatorService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<AtomIndicatorVO>> list(@ModelAttribute AtomIndicatorDTO query) {
        return Result.success(atomIndicatorService.list(query));
    }

    @GetMapping("/{id}")
    public Result<AtomIndicatorVO> getById(@PathVariable("id") String id) {
        return Result.success(atomIndicatorService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody AtomIndicatorDTO dto) {
        return Result.success(atomIndicatorService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<AtomIndicatorVO> update(
                                          @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                          @PathVariable("id") String id,
                                          @RequestBody AtomIndicatorDTO dto) {
        return Result.success(atomIndicatorService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("id") String id) {
        return Result.success(atomIndicatorService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<AtomIndicatorVO> submit(
                                          @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                          @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                          @PathVariable("id") String id) {
        return Result.success(atomIndicatorService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(atomIndicatorService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<AtomIndicatorVO> rollback(
                                            @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                            @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                            @PathVariable("id") String id,
                                            @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(atomIndicatorService.rollback(id, versionNumber, loginUser.getUserName()));
    }

}
