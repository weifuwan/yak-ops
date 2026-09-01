package io.yak.ops.business.taskcatalog.controller.v1;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskAssetCatalogReconciler;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TaskCatalogControllerReconcilerTest {

  @Test
  void sourceSpecificDiscoveryReconcilesOnlyMatchingProjection() {
    TaskCatalogService service = Mockito.mock(TaskCatalogService.class);
    TaskAssetCatalogReconciler quality = Mockito.mock(TaskAssetCatalogReconciler.class);
    TaskAssetCatalogReconciler integration = Mockito.mock(TaskAssetCatalogReconciler.class);
    when(quality.source()).thenReturn(TaskAssetSource.DATA_QUALITY);
    when(integration.source()).thenReturn(TaskAssetSource.DATA_INTEGRATION);
    when(service.list("DATA_QUALITY", "ONLINE", null)).thenReturn(List.of());
    TaskCatalogController controller = new TaskCatalogController(
        service, List.of(quality, integration));

    controller.list("DATA_QUALITY", "ONLINE", null);

    verify(quality).reconcile();
    verify(integration, never()).reconcile();
  }

  @Test
  void unfilteredDiscoveryReconcilesAllSourceProjections() {
    TaskCatalogService service = Mockito.mock(TaskCatalogService.class);
    TaskAssetCatalogReconciler quality = Mockito.mock(TaskAssetCatalogReconciler.class);
    TaskAssetCatalogReconciler integration = Mockito.mock(TaskAssetCatalogReconciler.class);
    when(service.list(null, "ONLINE", null)).thenReturn(List.of());
    TaskCatalogController controller = new TaskCatalogController(
        service, List.of(quality, integration));

    controller.list(null, "ONLINE", null);

    verify(quality).reconcile();
    verify(integration).reconcile();
  }
}
