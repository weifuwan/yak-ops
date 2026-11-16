package io.yak.ops.api.controller.modeling;

/** HTTP API for Modifier. */
@RestController
@RequestMapping("/projects/{projectCode}/modeling/modifiers")
public class ModifierController extends BaseController {

    @Resource
    private ModifierService modifierService;

    @PostMapping("/page")
    public Result<PageInfo<ModifierVO>> page(@RequestBody ModifierDTO query) {
        checkPageParams(query.getPageNo(), query.getPageSize());
        return Result.success(PageInfo.of(modifierService.page(query)));
    }

    @GetMapping("/list")
    public Result<List<ModifierVO>> list(@ModelAttribute ModifierDTO query) {
        return Result.success(modifierService.list(query));
    }

    @GetMapping("/{id}")
    public Result<ModifierVO> getById(@PathVariable("id") String id) {
        return Result.success(modifierService.getById(id));
    }

    @PostMapping
    @OperatorLog(auditType = AuditType.MODELING_CREATE)
    public Result<String> create(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @RequestBody ModifierDTO dto) {
        return Result.success(modifierService.create(dto, loginUser.getUserName()));
    }

    @PutMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<ModifierVO> update(
                                     @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                     @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                     @PathVariable("id") String id,
                                     @RequestBody ModifierDTO dto) {
        return Result.success(modifierService.update(id, dto, loginUser.getUserName()));
    }

    @DeleteMapping("/{id}")
    @OperatorLog(auditType = AuditType.MODELING_DELETE)
    public Result<String> delete(
                                 @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                 @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                 @PathVariable("id") String id) {
        return Result.success(modifierService.delete(id));
    }

    @PostMapping("/{id}/submit")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<ModifierVO> submit(
                                     @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                     @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                     @PathVariable("id") String id) {
        return Result.success(modifierService.submit(id, loginUser.getUserName()));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ModelingAssetVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(modifierService.listVersions(id));
    }

    @PostMapping("/{id}/rollback")
    @OperatorLog(auditType = AuditType.MODELING_UPDATE)
    public Result<ModifierVO> rollback(
                                       @Parameter(hidden = true) @RequestAttribute(value = Constants.SESSION_USER) User loginUser,
                                       @Parameter(hidden = true) @PathVariable("projectCode") long projectCode,
                                       @PathVariable("id") String id,
                                       @RequestParam("versionNumber") Integer versionNumber) {
        return Result.success(modifierService.rollback(id, versionNumber, loginUser.getUserName()));
    }

}
