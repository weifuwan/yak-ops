package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.quality.alert.QualityAlertRecorder;
import io.yak.ops.business.quality.asset.QualityTableAssetManager;
import io.yak.ops.business.quality.asset.QualityTableAssetReader;
import io.yak.ops.business.quality.asset.QualityTableCandidateReader;
import io.yak.ops.business.quality.asset.QualityTableTargetPolicy;
import io.yak.ops.business.quality.execution.QualityExecutionDispatcher;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.execution.QualityExecutionPlanFactory;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.execution.QualityExecutionWorker;
import io.yak.ops.business.quality.execution.QualityMetricEvaluator;
import io.yak.ops.business.quality.execution.QualitySqlCompiler;
import io.yak.ops.business.quality.gateway.datasource.DataSourceQualityCatalogAdapter;
import io.yak.ops.business.quality.monitor.QualityMonitorManager;
import io.yak.ops.business.quality.monitor.QualityMonitorPolicy;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.quality.monitor.QualityMonitorSettingsPolicy;
import io.yak.ops.business.quality.monitor.QualityRulePolicy;
import io.yak.ops.business.quality.schedule.QualityScheduleCalculator;
import io.yak.ops.business.quality.schedule.QualityScheduleEngineBridge;
import io.yak.ops.business.quality.schedule.QualityScheduleHandler;
import io.yak.ops.business.quality.schedule.QualityScheduleLifecycle;
import io.yak.ops.business.quality.schedule.QualityScheduleReconciler;
import io.yak.ops.business.quality.task.QualityTaskCatalogReconciler;
import io.yak.ops.business.quality.task.QualityTaskExecutor;
import io.yak.ops.business.quality.task.QualityTaskPublisher;
import io.yak.ops.business.quality.task.QualityTaskRevisionProvider;
import io.yak.ops.business.quality.template.CustomTemplateManager;
import io.yak.ops.business.quality.template.CustomTemplatePolicy;
import io.yak.ops.business.quality.template.CustomTemplateReader;
import io.yak.ops.business.quality.template.QualityTemplateReader;
import io.yak.ops.business.quality.template.TemplateFolderManager;
import io.yak.ops.business.quality.template.TemplateFolderReader;
import io.yak.ops.business.quality.workspace.QualityExecutionLogProjector;
import io.yak.ops.business.quality.workspace.QualityOverviewReader;
import io.yak.ops.business.quality.workspace.QualityWorkspaceReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class QualityRoleConventionTest {

  @Test
  void internalApplicationRolesStayExplicitComponents() {
    for (Class<?> role :
        List.of(
            QualityTableAssetManager.class,
            QualityTableAssetReader.class,
            QualityTableCandidateReader.class,
            QualityTableTargetPolicy.class,
            QualityMonitorManager.class,
            QualityMonitorReader.class,
            QualityMonitorPolicy.class,
            QualityRulePolicy.class,
            QualityMonitorSettingsPolicy.class,
            QualityExecutionManager.class,
            QualityExecutionReader.class,
            QualityExecutionPlanFactory.class,
            QualityExecutionDispatcher.class,
            QualityExecutionWorker.class,
            QualityMetricEvaluator.class,
            QualitySqlCompiler.class,
            QualityAlertRecorder.class,
            QualityScheduleCalculator.class,
            QualityScheduleEngineBridge.class,
            QualityScheduleHandler.class,
            QualityScheduleLifecycle.class,
            QualityScheduleReconciler.class,
            QualityTaskPublisher.class,
            QualityTaskCatalogReconciler.class,
            QualityTaskRevisionProvider.class,
            QualityTaskExecutor.class,
            CustomTemplateManager.class,
            CustomTemplateReader.class,
            CustomTemplatePolicy.class,
            QualityTemplateReader.class,
            TemplateFolderManager.class,
            TemplateFolderReader.class,
            QualityWorkspaceReader.class,
            QualityOverviewReader.class,
            QualityExecutionLogProjector.class,
            DataSourceQualityCatalogAdapter.class)) {
      assertThat(role.getAnnotation(Component.class))
          .as("%s must remain an explicit internal component role", role.getSimpleName())
          .isNotNull();
      assertThat(role.getAnnotation(Service.class))
          .as("%s must not masquerade as a generic Application Service", role.getSimpleName())
          .isNull();
    }
  }
}
