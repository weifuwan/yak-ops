package io.yak.ops.business.task.submission.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.submission.PreparedTaskSubmission;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.entity.task.TaskGroupQueueEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Checks read-only revalidation and atomic admission inputs. */
class TaskSubmissionTransactionServiceTest {
    private final TaskSubmissionTransactionServiceImpl service = new TaskSubmissionTransactionServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final TaskGroupQueueRepository claims = mock(TaskGroupQueueRepository.class);
    private final TaskChannel plugin = mock(TaskChannel.class);
    private final DefinitionEntity definition = new DefinitionEntity();
    private final ReadonlyConfig parameters = ReadonlyConfig.fromMap(java.util.Map.of());
    private final TaskExecutionIdentity context = new TaskExecutionIdentity("ws", "definition-1", 2, "instance-1");
    private final ExecutionResource target = new ExecutionResource("DATA_SYNC_TARGET", "source/table");
    private final TaskExecutionResources plan = new TaskExecutionResources(List.of(target), "resource-v1");

    TaskSubmissionTransactionServiceTest() {
        inject("definitions", definitions);
        inject("versions", versions);
        inject("instances", instances);
        inject("claims", claims);

        definition.setWorkspaceId("ws");
        definition.setId("definition-1");
        definition.setCurrentVersion(2);
        definition.setPublishedVersion(2);
        definition.setPublishedDebug(true);
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.setTaskType("DATA_SYNC");
        definition.setDebug(true);
        when(definitions.queryByIdForUpdate("ws", "definition-1")).thenReturn(Optional.of(definition));
        when(versions.queryCurrent("ws", "definition-1", 2)).thenReturn(Optional.of(new DefinitionVersionEntity()));
        when(plugin.type()).thenReturn("DATA_SYNC");
        when(plugin.plan(parameters, context)).thenReturn(plan);
    }

    @Test
    void insertsOneInstanceAndExclusiveResource() {
        var result = service.admit(request());
        assertEquals("instance-1", result.instanceId());
        assertEquals(TaskInstanceStatus.SUBMITTED, result.status());

        ArgumentCaptor<InstanceEntity> instance = ArgumentCaptor.forClass(InstanceEntity.class);
        ArgumentCaptor<TaskGroupQueueEntity> claim = ArgumentCaptor.forClass(TaskGroupQueueEntity.class);
        verify(instances).add(instance.capture());
        verify(claims).add(claim.capture());

        assertEquals(TaskTriggerType.MANUAL, instance.getValue().getTriggerType());
        assertEquals(2, instance.getValue().getDefinitionVersion());
        assertEquals(true, instance.getValue().isDebug());
        assertEquals("resource-v1", instance.getValue().getResourceRevision());
        assertEquals("instance-1", claim.getValue().getInstanceId());
        assertEquals("source/table", claim.getValue().getResourceKey());
        verify(plugin, never()).createTask(any(), any(), any(), anyBoolean());
    }

    @Test
    void draftSaveDuringPreparationDoesNotChangeAdmittedVersionOrDebug() {
        PreparedTaskSubmission prepared = request();
        definition.setCurrentVersion(3);
        definition.setDebug(false);
        definition.setRevision(9);

        service.admit(prepared);

        ArgumentCaptor<InstanceEntity> instance = ArgumentCaptor.forClass(InstanceEntity.class);
        verify(instances).add(instance.capture());
        assertEquals(2, instance.getValue().getDefinitionVersion());
        assertEquals(true, instance.getValue().isDebug());
        verify(versions, never()).queryCurrent("ws", "definition-1", 3);
    }

    @Test
    void publicationChangeDuringPreparationRejectsTheStaleAdmission() {
        PreparedTaskSubmission prepared = request();
        definition.setPublishedVersion(3);
        assertThrows(BusinessException.class, () -> service.admit(prepared));
        verify(instances, never()).add(any());
        verify(claims, never()).add(any());
    }

    @Test
    void debugOnlyPublicationChangeDuringPreparationRejectsTheStaleAdmission() {
        PreparedTaskSubmission prepared = request();
        definition.setPublishedDebug(false);
        assertThrows(BusinessException.class, () -> service.admit(prepared));
        verify(instances, never()).add(any());
        verify(claims, never()).add(any());
    }

    @Test
    void usesPreparedResourcesWithoutReplanningUnderDefinitionLock() {
        service.admit(request());
        verify(plugin, never()).plan(any(), any());
        verify(instances).add(any());
        verify(claims).add(any());
    }

    @Test
    void replayDoesNotAcquireAnotherClaim() {
        InstanceEntity existing = new InstanceEntity();
        existing.setId("existing");
        existing.setWorkspaceId("ws");
        existing.setDefinitionVersion(1);
        existing.setRequestFingerprint("MANUAL:definition-1");
        existing.setStatus(TaskInstanceStatus.FINISHED);
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.of(existing));

        var result = service.admit(request());
        assertEquals("existing", result.instanceId());
        assertEquals(true, result.replayed());
        verify(definitions, never()).queryByIdForUpdate("ws", "definition-1");
        verify(claims, never()).add(any());
    }

    @Test
    void workflowAdmissionPersistsTheTriggerWithExplicitWorkspace() {
        PreparedTaskSubmission workflow = new PreparedTaskSubmission(
                context,
                true,
                TaskTriggerType.WORKFLOW,
                "workflow-123",
                "WORKFLOW:definition-1",
                "system",
                parameters,
                plan,
                plugin,
                null,
                null,
                null);

        service.admit(workflow);

        ArgumentCaptor<InstanceEntity> saved = ArgumentCaptor.forClass(InstanceEntity.class);
        verify(instances).add(saved.capture());
        assertEquals(TaskTriggerType.WORKFLOW, saved.getValue().getTriggerType());
        verify(instances).findByRequest("ws", TaskTriggerType.WORKFLOW, "workflow-123");
    }

    private PreparedTaskSubmission request() {
        return new PreparedTaskSubmission(
                context,
                true,
                TaskTriggerType.MANUAL,
                "request-123",
                "MANUAL:definition-1",
                "actor",
                parameters,
                plan,
                plugin,
                null,
                null,
                null);
    }

    private void inject(String name, Object dependency) {
        try {
            Field field = TaskSubmissionTransactionServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
