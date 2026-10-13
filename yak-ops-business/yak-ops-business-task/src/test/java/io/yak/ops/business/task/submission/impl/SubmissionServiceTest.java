package io.yak.ops.business.task.submission.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.submission.AdmissionService;
import io.yak.ops.business.task.submission.SubmissionResult;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** Validates request replay and admission without physical runtime submission. */
class SubmissionServiceTest {
    private final SubmissionServiceImpl service = new SubmissionServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final TaskPluginRegistry registry = mock(TaskPluginRegistry.class);
    private final TaskValidationContext validation = mock(TaskValidationContext.class);
    private final AdmissionService admission = mock(AdmissionService.class);
    private final ExecutableTaskPlugin plugin = mock(ExecutableTaskPlugin.class);
    private final TaskParameters params = mock(TaskParameters.class);

    SubmissionServiceTest() {
        inject("definitions", definitions);
        inject("versions", versions);
        inject("instances", instances);
        inject("attempts", attempts);
        inject("plugins", registry);
        inject("validationContext", validation);
        inject("admission", admission);
        WorkspaceContext.bind("ws");

        DefinitionEntity definition = new DefinitionEntity();
        definition.setId("definition-1");
        definition.setWorkspaceId("ws");
        definition.setTaskType("DATA_SYNC");
        definition.setCurrentVersion(2);
        definition.setStatus(DefinitionStatus.PUBLISHED);
        when(definitions.queryById("ws", "definition-1")).thenReturn(Optional.of(definition));
        DefinitionVersionEntity version = new DefinitionVersionEntity();
        version.setParameters("{}");
        when(versions.queryCurrent("ws", "definition-1", 2)).thenReturn(Optional.of(version));
        when(registry.get("DATA_SYNC")).thenReturn(plugin);
        when(plugin.parse("{}")).thenReturn(params);
        when(plugin.plan(any(), any())).thenReturn(new TaskExecutionPlan(List.of(), "config-v1"));
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void admissionDoesNotStartThePlugin() {
        SubmissionResult accepted =
                new SubmissionResult("instance-1", "attempt-1", 2, TaskInstanceStatus.SUBMITTED, false);
        when(admission.admit(any())).thenReturn(accepted);
        assertEquals(accepted, service.submitManual("definition-1", "request-123", "actor"));
        verify(plugin, never()).start(any(), any(), any());
    }

    @Test
    void replaysStoredInstanceEvenWhenDefinitionIsUnavailable() {
        InstanceEntity instance = existing("definition-1");
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.of(instance));
        AttemptEntity attempt = new AttemptEntity();
        attempt.setId("attempt-1");
        when(attempts.findByInstance("ws", "instance-1")).thenReturn(List.of(attempt));

        var result = service.submitManual("definition-1", "request-123", "actor");
        assertEquals(true, result.replayed());
        assertEquals(2, result.definitionVersion());
        verify(definitions, never()).queryById("ws", "definition-1");
        verify(admission, never()).admit(any());
    }

    @Test
    void rejectsConflictingRequestIdentity() {
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.of(existing("another-definition")));
        assertThrows(BusinessException.class, () -> service.submitManual("definition-1", "request-123", "actor"));
        verify(admission, never()).admit(any());
    }

    @Test
    void handlesConcurrentDuplicateInsertAsIdempotentReplay() {
        when(admission.admit(any())).thenThrow(new DuplicateKeyException("request key already committed"));
        when(instances.findByRequest("ws", TaskTriggerType.MANUAL, "request-123"))
                .thenReturn(Optional.empty(), Optional.of(existing("definition-1")));
        AttemptEntity attempt = new AttemptEntity();
        attempt.setId("attempt-1");
        when(attempts.findByInstance("ws", "instance-1")).thenReturn(List.of(attempt));

        var result = service.submitManual("definition-1", "request-123", "actor");
        assertEquals("instance-1", result.instanceId());
        assertEquals(true, result.replayed());
    }

    @Test
    void rejectsMissingOrMalformedRequestKey() {
        assertThrows(BusinessException.class, () -> service.submitManual("definition-1", "bad", "actor"));
        assertThrows(BusinessException.class, () -> service.submitManual("definition-1", null, "actor"));
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
