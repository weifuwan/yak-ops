package io.yak.ops.business.quality.task;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QualityTaskCatalogReconcilerTest {

  @Test
  void currentProjectDiscoveryRepairsPreExistingMonitorProjection() {
    QualityMonitorRepository monitors = Mockito.mock(QualityMonitorRepository.class);
    QualityTaskPublisher publisher = Mockito.mock(QualityTaskPublisher.class);
    CurrentProject currentProject = Mockito.mock(CurrentProject.class);
    @SuppressWarnings("unchecked")
    PageData<Monitor> page = Mockito.mock(PageData.class);
    Monitor monitor = Mockito.mock(Monitor.class);
    when(monitor.id()).thenReturn(42L);
    when(currentProject.isPresent()).thenReturn(true);
    when(currentProject.requireProjectId()).thenReturn(7L);
    when(page.records()).thenReturn(List.of(monitor));
    when(page.pages()).thenReturn(1L);
    when(monitors.pageMonitors(Mockito.any())).thenReturn(page);
    when(monitors.findMonitor(42L)).thenReturn(Optional.of(monitor));
    QualityTaskCatalogReconciler reconciler = new QualityTaskCatalogReconciler(
        monitors, publisher, currentProject);

    reconciler.reconcile();

    verify(publisher).sync(monitor);
    org.assertj.core.api.Assertions.assertThat(reconciler.source())
        .isEqualTo(TaskAssetSource.DATA_QUALITY);
  }
}
