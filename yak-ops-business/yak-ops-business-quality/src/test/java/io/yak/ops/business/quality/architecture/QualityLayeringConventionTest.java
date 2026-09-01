package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.dao.QualityAnalyticsDao;
import io.yak.ops.business.quality.dao.QualityCatalogDao;
import io.yak.ops.business.quality.dao.QualityExecutionDao;
import io.yak.ops.business.quality.dao.QualityMonitorDao;
import io.yak.ops.business.quality.domain.QualityDomain;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import io.yak.ops.business.quality.repository.QualityAlertRepository;
import io.yak.ops.business.quality.repository.QualityExecutionRepository;
import io.yak.ops.business.quality.repository.QualityExecutionWorkspaceRepository;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.quality.repository.QualityOverviewRepository;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import io.yak.ops.business.quality.repository.QualityTaskRevisionRepository;
import io.yak.ops.business.quality.repository.QualityTemplateRepository;
import io.yak.ops.business.quality.repository.QualityWorkspaceRepository;
import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityRulePO;
import io.yak.ops.common.bean.po.quality.QualityRuleTemplatePO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import io.yak.ops.common.bean.po.quality.QualityTaskRevisionPO;
import io.yak.ops.common.bean.po.quality.QualityTemplateFolderPO;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityLayeringConventionTest {

  @Test
  void repositoryBoundariesShouldNotExposeHttpOrPersistenceContracts() {
    assertCleanBoundaries(List.of(
        QualityTemplateRepository.class,
        QualityTableAssetRepository.class,
        QualityMonitorRepository.class,
        QualityExecutionRepository.class,
        QualityTaskRevisionRepository.class,
        QualityAlertRepository.class,
        CustomTemplateRepository.class,
        QualityWorkspaceRepository.class,
        QualityOverviewRepository.class,
        QualityExecutionWorkspaceRepository.class));
  }

  @Test
  void repositoryPagingUsesSharedPageData() throws Exception {
    Method tableAssets = QualityTableAssetRepository.class.getMethod(
        "pageTableAssets", QualityQuery.TableAsset.class);
    Method monitors = QualityMonitorRepository.class.getMethod(
        "pageMonitors", QualityQuery.Monitor.class);
    Method executions = QualityExecutionRepository.class.getMethod(
        "pageExecutions", QualityQuery.Execution.class);
    Method workspace = QualityExecutionWorkspaceRepository.class.getMethod(
        "page", QualityQuery.ExecutionWorkspace.class);
    Method workspaceRules = QualityExecutionWorkspaceRepository.class.getMethod(
        "pageRules", QualityQuery.ExecutionWorkspace.class);
    for (Method method : List.of(tableAssets, monitors, executions, workspace, workspaceRules)) {
      assertThat(((ParameterizedType) method.getGenericReturnType()).getRawType())
          .as(method.getName())
          .isEqualTo(PageData.class);
    }
  }

  @Test
  void qualityDomainDoesNotReintroducePrivatePagingContainers() {
    assertThat(Arrays.stream(QualityDomain.class.getDeclaredClasses())
            .map(Class::getSimpleName))
        .doesNotContain("Page", "PageData", "PagingData");
  }

  @Test
  void daoBoundariesShouldNotExposeHttpContracts() {
    for (Class<?> type : List.of(
        QualityCatalogDao.class,
        QualityMonitorDao.class,
        QualityExecutionDao.class,
        QualityAnalyticsDao.class)) {
      for (Method method : type.getMethods()) {
        assertThat(signature(method))
            .as("DAO transport boundary: %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(".bean.dto.quality")
            .doesNotContain(".bean.vo.quality");
      }
    }
  }

  @Test
  void tablePosShouldStayOneToOneWithExistingQualityTables() {
    assertTable(QualityRuleTemplatePO.class, "yak_quality_rule_template");
    assertTable(QualityMonitorPO.class, "yak_quality_monitor");
    assertTable(QualityRulePO.class, "yak_quality_rule");
    assertTable(QualityExecutionPO.class, "yak_quality_execution");
    assertTable(QualityRuleExecutionPO.class, "yak_quality_rule_execution");
    assertTable(QualityTableAssetPO.class, "yak_quality_table_asset");
    assertTable(QualityMonitorSettingPO.class, "yak_quality_monitor_setting");
    assertTable(QualityAlertEventPO.class, "yak_quality_alert_event");
    assertTable(QualityTemplateFolderPO.class, "yak_quality_template_folder");
    assertTable(QualityTaskRevisionPO.class, "yak_quality_monitor_revision");
  }

  private void assertCleanBoundaries(List<Class<?>> types) {
    for (Class<?> type : types) {
      for (Method method : type.getMethods()) {
        assertThat(signature(method))
            .as("Repository boundary: %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(".bean.dto.quality")
            .doesNotContain(".bean.vo.quality")
            .doesNotContain(".bean.po.quality")
            .doesNotContain("com.baomidou.mybatisplus");
      }
    }
  }

  private String signature(Method method) {
    StringBuilder signature = new StringBuilder(method.getGenericReturnType().getTypeName());
    for (var parameter : method.getGenericParameterTypes()) {
      signature.append('|').append(parameter.getTypeName());
    }
    return signature.toString();
  }

  private void assertTable(Class<?> type, String expected) {
    TableName tableName = type.getAnnotation(TableName.class);
    assertThat(tableName).as(type.getSimpleName() + " @TableName").isNotNull();
    assertThat(tableName.value()).isEqualTo(expected);
  }
}
