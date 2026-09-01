package io.yak.ops.business.quality.task;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.taskcatalog.spi.TaskAssetCatalogReconciler;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Repairs the current Project's Quality Task Catalog projection for pre-existing monitors. */
@Component
@ConditionalOnQualityEnabled
public class QualityTaskCatalogReconciler implements TaskAssetCatalogReconciler {
  private static final Logger LOG = LoggerFactory.getLogger(QualityTaskCatalogReconciler.class);
  private static final int PAGE_SIZE = 200;
  private static final long RECONCILE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

  private final QualityMonitorRepository monitorRepository;
  private final QualityTaskPublisher taskPublisher;
  private final CurrentProject currentProject;
  private final ConcurrentMap<Long, Long> lastReconciled = new ConcurrentHashMap<>();
  private final ConcurrentMap<Long, Object> projectLocks = new ConcurrentHashMap<>();

  public QualityTaskCatalogReconciler(
      QualityMonitorRepository monitorRepository,
      QualityTaskPublisher taskPublisher,
      CurrentProject currentProject) {
    this.monitorRepository = monitorRepository;
    this.taskPublisher = taskPublisher;
    this.currentProject = currentProject;
  }

  @Override
  public TaskAssetSource source() {
    return TaskAssetSource.DATA_QUALITY;
  }

  @Override
  public void reconcile() {
    if (!currentProject.isPresent()) return;
    long projectId = currentProject.requireProjectId();
    Object lock = projectLocks.computeIfAbsent(projectId, ignored -> new Object());
    synchronized (lock) {
      long now = System.nanoTime();
      Long previous = lastReconciled.get(projectId);
      if (previous != null && now - previous < RECONCILE_INTERVAL_NANOS) return;
      reconcileCurrentProject();
      lastReconciled.put(projectId, now);
    }
  }

  private void reconcileCurrentProject() {
    int pageNo = 1;
    while (true) {
      PageData<Monitor> page = monitorRepository.pageMonitors(query(pageNo));
      for (Monitor summary : page.records()) {
        monitorRepository.findMonitor(summary.id()).ifPresent(this::syncSafely);
      }
      if (pageNo >= page.pages()) break;
      pageNo++;
    }
  }

  private QualityQuery.Monitor query(int pageNo) {
    return new QualityQuery.Monitor(
        pageNo,
        PAGE_SIZE,
        null,
        null,
        null,
        false,
        null,
        false,
        null,
        null,
        null);
  }

  private void syncSafely(Monitor monitor) {
    try {
      taskPublisher.sync(monitor);
    } catch (RuntimeException exception) {
      LOG.warn(
          "Unable to reconcile Quality monitor {} into Task Catalog: {}",
          monitor.id(),
          exception.getMessage());
    }
  }
}
