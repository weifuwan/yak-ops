package io.yak.ops.business.task.execution.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.execution.AdmissionRequest;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Checks read-only revalidation and atomic admission inputs. */
class AdmissionServiceTest {
    private final AdmissionServiceImpl service = new AdmissionServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final ExecutionClaimRepository claims = mock(ExecutionClaimRepository.class);
    private final ExecutableTaskPlugin plugin = mock(ExecutableTaskPlugin.class);
    private final TaskParameters parameters = mock(TaskParameters.class);
    private final TaskExecutionContext context =
            new TaskExecutionContext("ws", "definition-1", 2, "instance-1", "attempt-1");
    private final ExecutionResource target = new ExecutionResource("DATA_SYNC_TARGET", "source/table");
    private final TaskExecutionPlan plan = new TaskExecutionPlan(List.of(target), "resource-v1");

    AdmissionServiceTest() {
        inject("definitions", definitions);
        inject("versions", versions);
        inject("instances", instances);
        inject("attempts", attempts);
        inject("claims", claims);
        WorkspaceContext.bind("ws");

        DefinitionEntity definition = new DefinitionEntity();
        definition.setWorkspaceId("ws");
        definition.setId("definition-1");
        definition.setCurrentVersion(2);
        definition.setStatus(DefinitionStatus.PUBLISHED);
        definition.setTaskType("DATA_SYNC");
        when(definitions.queryByIdForUpdate("ws", "definition-1")).thenReturn(Optional.of(definition));
        when(versions.queryCurrent("ws", "definition-1", 2))
                .thenReturn(Optional.of(new DefinitionVersionEntity()));
        when(plugin.type()).thenReturn("DATA_SYNC");
        when(plugin.plan(parameters, context)).thenReturn(plan);
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void insertsOneInstanceAttemptAndExclusiveResource() {
        var result = service.admit(request());
        assertEquals("instance-1", result.instanceId());
        assertEquals("attempt-1", result.attemptId());
        assertEquals(TaskInstanceStatus.SUBMITTED, result.status());

        ArgumentCaptor<InstanceEntity> instance = ArgumentCaptor.forClass(InstanceEntity.class);
        ArgumentCaptor<AttemptEntity> attempt = ArgumentCaptor.forClass(AttemptEntity.class);
        ArgumentCaptor<ExecutionClaimEntity> claim = ArgumentCaptor.forClass(ExecutionClaimEntity.class);
        verify(instances).add(instance.capture());
        verify(attempts).add(attempt.capture());
        verify(claims).add(claim.capture());

        assertEquals(TaskTriggerType.MANUAL, instance.getValue().getTriggerType());
        assertEquals(2, instance.getValue().getDefinitionVersion());
        assertEquals("resource-v1", instance.getValue().getResourceRevision());
        assertEquals(TaskAttemptStatus.PENDING, attempt.getValue().getStatus());
        assertEquals("instance-1", claim.getValue().getInstanceId());
        assertEquals("source/table", claim.getValue().getResourceKey());
        verify(plugin, never()).start(any(), any(), any());
    }

    @Test
    void rejectsResourceBindingChangeBeforeDatabaseWrites() {
        when(plugin.plan(parameters, context)).thenReturn(new TaskExecutionPlan(List.of(target), "resource-v2"));
        assertThrows(BusinessException.class, () -> service.admit(request()));
        verify(instances, never()).add(any());
        verify(attempts, never()).add(any());
        verify(claims, never()).add(any());
    }

    @Test
    void replayDoesNotAcquireAnotherClaim() {
        InstanceEntity existing = new InstanceEntity();
        existing.setId("existing");
        existing.setWorkspaceId("ws");
        existing.setDefinitionVersion(1);
        existing.setRequestFingerprint("MANUAL:definition-1");
        existing.setStatus(TaskInstanceStatus.FINISHED);
        AttemptEntity prior = new AttemptEntity();
        prior.setId("prior-attempt");
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.of(existing));
        when(attempts.findByInstance("ws", "existing")).thenReturn(List.of(prior));

        var result = service.admit(request());
        assertEquals("existing", result.instanceId());
        assertEquals("prior-attempt", result.attemptId());
        assertEquals(true, result.replayed());
        verify(definitions, never()).queryByIdForUpdate("ws", "definition-1");
        verify(claims, never()).add(any());
    }

    private AdmissionRequest request() {
        return new AdmissionRequest(context, "request-123", "MANUAL:definition-1", "actor", parameters, plan, plugin);
    }

    private void inject(String name, Object dependency) {
        try {
            Field field = AdmissionServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
