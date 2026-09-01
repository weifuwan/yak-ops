package io.yak.ops.business.taskcatalog.spi;

import io.yak.ops.spi.task.model.TaskAssetSource;

/** Allows one source domain to repair its Task Catalog projection before discovery. */
public interface TaskAssetCatalogReconciler {
  TaskAssetSource source();
  void reconcile();
}
