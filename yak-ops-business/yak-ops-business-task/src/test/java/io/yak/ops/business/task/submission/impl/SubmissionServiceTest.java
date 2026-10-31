package io.yak.ops.business.task.submission.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.submission.PreparedTaskSubmission;
import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.business.task.submission.TaskSubmissionCommand;
import io.yak.ops.business.task.submission.TaskSubmissionTransactionService;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.api.validation.TaskValidationContextFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/** Validates request replay and admission without physical runtime submission. */
class SubmissionServiceTest {
    private final SubmissionServiceImpl service = new SubmissionServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final TaskPluginManager registry = mock(TaskPluginManager.class);
    private final TaskValidationContext validation = mock(TaskValidationContext.class);
    private final TaskValidationContextFactory validationContexts = mock(TaskValidationContextFactory.class);
    private final TaskSubmissionTransactionService admission = mock(TaskSubmissionTransactionService.class);
    private final TaskChannel plugin = mock(TaskChannel.class);
    private final DefinitionEntity definition = new DefinitionEntity();
    private final ReadonlyConfig params = ReadonlyConfig.fromMap(java.util.Map.of());

    SubmissionServiceTest() {
        inject("definitions", definitions);
        inject("versions", versions);
        inject("instances", instances);
        inject("plugins", registry);
        inject("validationContexts", validationContexts);
        inject("admission", admission);
        when(validationContexts.forWorkspace("ws")).thenReturn(validation);

        definition.setId("definition-1");
        definition.setWorkspaceId("ws");
        definition.setTaskType("DATA_SYNC");
        definition.setCurrentVersion(2);
        definition.setPublishedVersion(2);
        definition.setPublishedDebug(false);
        definition.setStatus(DefinitionStatus.PUBLISHED);
        when(definitions.queryById("ws", "definition-1")).thenReturn(Optional.of(definition));
        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setParameters("{}");
        version.setParameterSchemaVersion(2);
        when(plugin.parameterSchemaVersion()).thenReturn(2);
        when(versions.queryCurrent("ws", "definition-1", 2)).thenReturn(Optional.of(version));
        when(registry.get("DATA_SYNC")).thenReturn(plugin);
        when(plugin.parse(2, "{}")).thenReturn(params);
        when(plugin.plan(any(), any())).thenReturn(new TaskExecutionResources(List.of(), "config-v1"));
    }

    @Test
    void admissionDoesNotStartThePlugin() {
        SubmissionResult accepted = new SubmissionResult("instance-1", 2, TaskInstanceStatus.SUBMITTED, false);
        when(admission.admit(any())).thenReturn(accepted);
        assertEquals(accepted, service.submit(manual("request-123")));
        verify(plugin, never()).createTask(any(), any(), any(), anyBoolean());
    }

    @Test
    void preparesPublishedParametersAndDebugWhileNewerDraftRemainsEditable() {
        definition.setCurrentVersion(3);
        definition.setDebug(false);
        definition.setPublishedDebug(true);
        definition.setRevision(7);

        service.submit(manual("request-123"));

        ArgumentCaptor<PreparedTaskSubmission> prepared = ArgumentCaptor.forClass(PreparedTaskSubmission.class);
        verify(admission).admit(prepared.capture());
        assertEquals(2, prepared.getValue().identity().definitionVersion());
        assertTrue(prepared.getValue().debug());
        assertEquals(params, prepared.getValue().parameters());
        verify(versions, never()).queryCurrent("ws", "definition-1", 3);
    }

    @Test
    void publishedStatusWithoutCompletePublicationCannotFallBackToDraft() {
        definition.setPublishedDebug(null);
        assertThrows(BusinessException.class, () -> service.submit(manual("request-123")));
        definition.setPublishedDebug(false);
        definition.setPublishedVersion(null);
        assertThrows(BusinessException.class, () -> service.submit(manual("request-456")));
        verify(admission, never()).admit(any());
    }

    @Test
    void scheduledSubmissionCannotBypassTheOccurrenceGate() {
        TaskSubmissionCommand command = new TaskSubmissionCommand(
                "ws", "definition-1", TaskTriggerType.SCHEDULE, "scheduled-123", "scheduler", null, null, null);
        assertThrows(BusinessException.class, () -> service.submit(command));
        verify(admission, never()).admit(any());
    }

    @Test
    void replaysStoredInstanceEvenWhenDefinitionIsUnavailable() {
        InstanceEntity instance = existing("definition-1");
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.of(instance));

        var result = service.submit(manual("request-123"));
        assertEquals(true, result.replayed());
        assertEquals(2, result.definitionVersion());
        verify(definitions, never()).queryById("ws", "definition-1");
        verify(admission, never()).admit(any());
    }

    @Test
    void rejectsConflictingRequestIdentity() {
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.of(existing("another-definition")));
        assertThrows(BusinessException.class, () -> service.submit(manual("request-123")));
        verify(admission, never()).admit(any());
    }

    @Test
    void handlesConcurrentDuplicateInsertAsIdempotentReplay() {
        when(admission.admit(any())).thenThrow(new DuplicateKeyException("request key already committed"));
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.empty(), Optional.of(existing("definition-1")));

        var result = service.submit(manual("request-123"));
        assertEquals("instance-1", result.instanceId());
        assertEquals(true, result.replayed());
    }

    @Test
    void rejectsMissingOrMalformedRequestKey() {
        assertThrows(BusinessException.class, () -> service.submit(manual("bad")));
        assertThrows(BusinessException.class, () -> service.submit(manual(null)));
    }

    private TaskSubmissionCommand manual(String requestId) {
        return new TaskSubmissionCommand(
                "ws", "definition-1", TaskTriggerType.MANUAL, requestId, "actor", null, null, null);
    }

    private InstanceEntity existing(String definitionId) {
        InstanceEntity instance = new InstanceEntity();
        instance.setId("instance-1");
        instance.setWorkspaceId("ws");
        instance.setDefinitionId(definitionId);
        instance.setRequestFingerprint("MANUAL:" + definitionId);
        instance.setDefinitionVersion(2);
        instance.setStatus(TaskInstanceStatus.FINISHED);
        return instance;
    }

    private void inject(String name, Object dependency) {
        try {
            Field field = SubmissionServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
