package io.yak.ops.business.workflow.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.business.workflow.repository.NoopWorkflowDefinitionRepository;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkflowQualityTaskAssetBindingTest {

  @Test
  void dataQualityAssetCanBePinnedAndPublished() {
    WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    TaskRegistry registry = mock(TaskRegistry.class);
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    TaskAsset asset = new TaskAsset(
        12L,
        TaskAssetSource.DATA_QUALITY,
        "42",
        7L,
        "customers-quality",
        "QUALITY",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(12L, 101L, 1),
        Instant.parse("2026-09-01T00:00:00Z"),
        Instant.parse("2026-09-01T00:00:00Z"));
    TaskDefinition qualityDefinition = new TaskDefinition(
        "QUALITY",
        1,
        "customers-quality",
        "{\"projectId\":7}");
    when(catalog.get(12L)).thenReturn(asset);
    when(catalog.resolveRevision(12L, 101L)).thenReturn(new TaskAssetRevision(
        asset,
        new TaskSourceRevision(101L, 1, qualityDefinition, "checksum", 7L)));
    CurrentProject currentProject = () -> Optional.of(new ProjectContext(7L, "Project 7"));
    WorkflowDefinitionManager manager = new WorkflowDefinitionManager(
        runtime,
        registry,
        catalog,
        NoopWorkflowDefinitionRepository.INSTANCE,
        currentProject);

    WorkflowDefinitionVO created = manager.create(
        new WorkflowDefinitionCreateDTO("质量门禁工作流", "固定 Data Quality revision"));
    manager.update(created.id(), updateRequest());
    WorkflowDefinitionVO online = manager.online(created.id());

    assertEquals(1, online.activeVersionNo());
    assertEquals("QUALITY", online.nodes().getFirst().taskType());
    assertEquals(1, online.nodes().getFirst().taskRevisionNo());
  }

  private static WorkflowDefinitionUpdateDTO updateRequest() {
    WorkflowDefinitionUpdateDTO.NodeDTO node = new WorkflowDefinitionUpdateDTO.NodeDTO(
        "quality-node-1",
        "task-asset:12",
        12L,
        101L,
        1,
        160D,
        120D,
        1,
        0L,
        0L,
        0L,
        Map.of(),
        "ALL_SUCCESS",
        "FAIL_WORKFLOW");
    return new WorkflowDefinitionUpdateDTO(
        "质量门禁工作流",
        "固定 Data Quality revision",
        List.of(node),
        List.of(),
        Map.of(),
        Map.of(),
        0L,
        "CONTINUE_INDEPENDENT_BRANCHES");
  }
}
