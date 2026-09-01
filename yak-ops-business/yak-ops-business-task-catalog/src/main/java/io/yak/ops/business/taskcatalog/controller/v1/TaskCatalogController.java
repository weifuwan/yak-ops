package io.yak.ops.business.taskcatalog.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskAssetCatalogReconciler;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only discovery API for published task assets. */
@Tag(name = "任务资产目录")
@RestController
@RequestMapping("/api/v1/task-catalog/assets")
@ConditionalOnDataSourceEnabled
@ProjectScope(ProjectMigrationMode.PROJECT_OPTIONAL)
public class TaskCatalogController {

  private final TaskCatalogService service;
  private final List<TaskAssetCatalogReconciler> reconcilers;

  @Autowired
  public TaskCatalogController(
      TaskCatalogService service,
      List<TaskAssetCatalogReconciler> reconcilers) {
    this.service = service;
    this.reconcilers = List.copyOf(reconcilers);
  }

  /** Compatibility constructor for focused controller tests. */
  public TaskCatalogController(TaskCatalogService service) {
    this(service, List.of());
  }

  @Operation(summary = "查询已发布任务资产")
  @GetMapping
  public Result<List<TaskAsset>> list(
      @RequestParam(value = "source", required = false) String source,
      @RequestParam(value = "status", required = false, defaultValue = "ONLINE") String status,
      @RequestParam(value = "keyword", required = false) String keyword) {
    reconcile(source);
    return Result.success(service.list(source, status, keyword));
  }

  @Operation(summary = "查询任务资产详情")
  @GetMapping("/{assetId}")
  public Result<TaskAsset> detail(@PathVariable("assetId") long assetId) {
    return Result.success(service.get(assetId));
  }

  private void reconcile(String source) {
    String normalized = source == null ? "" : source.trim();
    for (TaskAssetCatalogReconciler reconciler : reconcilers) {
      if (normalized.isEmpty() || reconciler.source().name().equalsIgnoreCase(normalized)) {
        reconciler.reconcile();
      }
    }
  }
}
