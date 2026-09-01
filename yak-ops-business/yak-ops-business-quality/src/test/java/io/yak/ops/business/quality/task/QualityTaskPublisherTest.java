package io.yak.ops.business.quality.task;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityTaskRevision;
import io.yak.ops.business.quality.domain.execution.QualityExecutionDefinition;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.execution.QualityExecutionPlanFactory;
import io.yak.ops.business.quality.repository.QualityTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QualityTaskPublisherTest {

  @Test
  void publishesEnabledMonitorAsQualityTaskAsset() {
    QualityTaskRevisionRepository revisions = Mockito.mock(QualityTaskRevisionRepository.class);
    QualityExecutionPlanFactory plans = Mockito.mock(QualityExecutionPlanFactory.class);
    TaskCatalogService catalog = Mockito.mock(TaskCatalogService.class);
    QualityTaskPublisher publisher = new QualityTaskPublisher(
        revisions, plans, catalog, new ObjectMapper());
    Monitor monitor = monitor(true);
    QualityExecutionDefinition definition = definition();
    when(plans.freeze(monitor)).thenReturn(definition);
    when(revisions.findLatest(42L)).thenReturn(Optional.empty());
    when(revisions.insert(
        org.mockito.ArgumentMatchers.eq(42L),
        org.mockito.ArgumentMatchers.eq(1),
        org.mockito.ArgumentMatchers.eq("customers-quality"),
        anyString(),
        anyString()))
        .thenReturn(new QualityTaskRevision(
            101L, 7L, 42L, 1, "customers-quality", "{}", "digest", LocalDateTime.now()));

    publisher.sync(monitor);

    verify(catalog).publish(
        TaskAssetSource.DATA_QUALITY,
        "42",
        7L,
        "customers-quality",
        "QUALITY",
        101L,
        1);
  }

  @Test
  void disabledMonitorIsRemovedFromNewWorkflowDiscovery() {
    QualityTaskRevisionRepository revisions = Mockito.mock(QualityTaskRevisionRepository.class);
    QualityExecutionPlanFactory plans = Mockito.mock(QualityExecutionPlanFactory.class);
    TaskCatalogService catalog = Mockito.mock(TaskCatalogService.class);
    QualityTaskPublisher publisher = new QualityTaskPublisher(
        revisions, plans, catalog, new ObjectMapper());

    publisher.sync(monitor(false));

    verify(catalog).offlineSource(TaskAssetSource.DATA_QUALITY, "42");
    verify(plans, never()).freeze(org.mockito.ArgumentMatchers.any());
  }

  private Monitor monitor(boolean enabled) {
    Rule rule = new Rule(
        11L,
        42L,
        1L,
        "COLUMN_NOT_NULL",
        "id not null",
        RuleType.COLUMN_NOT_NULL,
        RuleScope.COLUMN,
        "完整性",
        "id",
        "GTE",
        BigDecimal.valueOf(99),
        null,
        List.of(),
        null,
        true,
        10);
    return new Monitor(
        42L,
        "customers-quality",
        null,
        9L,
        "mysql",
        "sales",
        null,
        "customers",
        null,
        "owner",
        enabled,
        CheckResult.NOT_RUN,
        null,
        null,
        LocalDateTime.now(),
        LocalDateTime.now(),
        1,
        List.of(rule));
  }

  private QualityExecutionDefinition definition() {
    return new QualityExecutionDefinition(
        7L,
        new MonitorSnapshot(
            42L,
            "customers-quality",
            9L,
            "mysql",
            "sales",
            null,
            "customers",
            null,
            "owner"),
        List.of(),
        RuleFailureAction.CONTINUE,
        false,
        NotifyChannel.MESSAGE,
        null,
        AlertLevel.WARNING);
  }
}
