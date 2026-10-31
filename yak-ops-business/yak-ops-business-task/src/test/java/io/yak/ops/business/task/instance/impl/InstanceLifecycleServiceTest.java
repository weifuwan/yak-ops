package io.yak.ops.business.task.instance.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.instance.DispatchTicket;
import io.yak.ops.business.task.instance.PendingRun;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.TaskGroupQueueRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.dao.repository.task.TaskCursorRepository;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies one-time dispatch fencing, cancellation, and terminal claim release. */
class InstanceLifecycleServiceTest {

    private final InstanceLifecycleServiceImpl service = new InstanceLifecycleServiceImpl();
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final TaskGroupQueueRepository claims = mock(TaskGroupQueueRepository.class);
    private final TaskCursorRepository cursors = mock(TaskCursorRepository.class);

    InstanceLifecycleServiceTest() {
        inject("instances", instances);
        inject("claims", claims);
        inject("cursors", cursors);
        when(cursors.frozen("ws", "i")).thenReturn(List.of());
    }

    @Test
    void claimsOnlyOneSubmittedInstance() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.SUBMITTED)));
        when(instances.claim("ws", "i", "owner-1", "token-1")).thenReturn(true);

        DispatchTicket ticket = service.claim("ws", "i", "owner-1", "token-1");

        assertEquals("i", ticket.instanceId());
        assertEquals("token-1", ticket.dispatchToken());
        assertNull(service.claim("ws", "i", "owner-1", "token-2"));
        verify(instances).claim("ws", "i", "owner-1", "token-2");
    }

    @Test
    void queuedInstanceDispatchKeepsItsAdmittedVersionAndDebug() {
        InstanceEntity queued = instance(TaskInstanceStatus.SUBMITTED);
        queued.setDefinitionVersion(2);
        queued.setDebug(true);
        when(instances.findByIdForUpdate("ws", "i")).thenReturn(Optional.of(queued));
        when(instances.claim("ws", "i", "owner-1", "token-1")).thenReturn(true);

        DispatchTicket ticket = service.claim("ws", "i", "owner-1", "token-1");

        assertEquals(2, ticket.definitionVersion());
        assertEquals(true, ticket.debug());
        assertEquals("d", ticket.definitionId());
    }

    @Test
    void recordsJobIdentityUnderTheWinningToken() {
        when(instances.markRunning("ws", "i", "token-1", "YAKFLOW", "job-1"))
                .thenReturn(true);

        service.started(ticket(), new TaskRuntimeReference("YAKFLOW", "job-1"));
        verify(instances).markRunning("ws", "i", "token-1", "YAKFLOW", "job-1");
    }

    @Test
    void deferredStartHasNoFabricatedJobIdAndLateBindingDoesNotChangeState() {
        when(instances.markRunning("ws", "i", "token-1", null, null)).thenReturn(true);
        when(instances.bindRuntimeReference("ws", "i", "token-1", "YAKFLOW", "job-1")).thenReturn(true);
        service.started(ticket(), null);
        service.runtimeReference(ticket(), new TaskRuntimeReference("YAKFLOW", "job-1"));
        verify(instances).bindRuntimeReference("ws", "i", "token-1", "YAKFLOW", "job-1");
        verify(instances, never()).markRunning("ws", "i", "token-1", "YAKFLOW", "job-1");
    }

    @Test
    void uncertainPreparationStoresItsPartialReportAndRetainsClaims() {
        String report = "{\"version\":3}";
        when(instances.findByIdForUpdate("ws", "i")).thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));
        when(instances.updateExecutionReport("ws", "i", "token-1", report)).thenReturn(true);
        service.uncertain(ticket(), report);
        verify(instances).updateExecutionReport("ws", "i", "token-1", report);
        verify(instances).markReconciling("ws", "i", "token-1");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void cancellationBeforeLaunchReleasesTheResourceClaim() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.SUBMITTED)));
        when(instances.cancelSubmitted("ws", "i")).thenReturn(true);

        assertEquals(TaskInstanceStatus.CANCELED, service.cancel("ws", "i"));
        verify(claims).releaseOwned("ws", "i");
        verify(instances, never()).markCancelling("ws", "i");
    }

    @Test
    void cancellationDuringLaunchKeepsTheClaimUntilTermination() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.LAUNCHING)));
        when(instances.markCancelling("ws", "i")).thenReturn(true);

        assertEquals(TaskInstanceStatus.CANCELLING, service.cancel("ws", "i"));
        verify(instances).markCancelling("ws", "i");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void terminalExecutionIsRecordedAndReleasesTheClaim() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));
        when(instances.markTerminal("ws", "i", "token-1", TaskInstanceStatus.FINISHED, null))
                .thenReturn(true);

        service.complete(ticket(), TaskOutcome.FINISHED);

        verify(claims).releaseOwned("ws", "i");
    }

    @Test
    void commitsAllFrozenCursorsOnlyForSuccessfulInstanceOutcome() {
        var row = new io.yak.ops.dao.repository.task.TaskCursorBatchRow(
                "a".repeat(64), "b".repeat(64), "TIMESTAMP", "updated_at", null,
                "2026-10-01T00:00:00", "2026-10-02T00:00:00", true, false, 7);
        when(cursors.frozen("ws", "i")).thenReturn(List.of(row));
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));
        when(instances.markTerminal("ws", "i", "token-1", TaskInstanceStatus.FINISHED, null))
                .thenReturn(true);

        service.complete(ticket(), TaskOutcome.FINISHED);
        verify(cursors).commit("ws", ticket().definitionId(), row);
        verify(claims).releaseOwned("ws", "i");
    }

    @Test
    void failedInstanceNeverCommitsCursor() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));
        when(instances.markTerminal("ws", "i", "token-1", TaskInstanceStatus.FAILED, "RUNTIME_FAILED"))
                .thenReturn(true);

        service.complete(ticket(), TaskOutcome.FAILED);
        verify(cursors, never()).commit(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancellationCompletionIsAuthoritative() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.CANCELLING)));
        when(instances.markTerminal("ws", "i", "token-1", TaskInstanceStatus.CANCELED, null))
                .thenReturn(true);

        service.complete(ticket(), TaskOutcome.CANCELED);

        verify(claims).releaseOwned("ws", "i");
    }

    @Test
    void pendingSubmissionIsRecoverableAfterLostHttpResponse() {
        when(instances.pending()).thenReturn(List.of(instance(TaskInstanceStatus.SUBMITTED)));
        assertEquals(List.of(new PendingRun("ws", "i")), service.pendingRuns());
        verify(instances, never()).claim("ws", "i", "owner-1", "token-1");
    }

    @Test
    void uncertainPhysicalOutcomeRetainsTheClaim() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));

        service.uncertain(ticket());

        verify(instances).markReconciling("ws", "i", "token-1");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void staleTerminalCallbackCannotReleaseAnotherLaunchersResources() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));

        assertThrows(IllegalStateException.class, () -> service.complete(ticket(), TaskOutcome.FINISHED));

        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void startupDoesNotResubmitThePreviousOwnersExecution() {
        InstanceEntity previous = instance(TaskInstanceStatus.RUNNING);
        previous.setOwnerIncarnation("previous-owner");
        previous.setDispatchToken("token-1");
        when(instances.activeByOtherOwner("new-owner")).thenReturn(List.of(previous));
        when(instances.findByIdForUpdate("ws", "i")).thenReturn(Optional.of(previous));

        service.reconcilePrevious("new-owner");

        verify(instances).markReconciling("ws", "i", "token-1");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void repeatedTerminalCallbackPreservesTheOriginalOutcome() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.FINISHED)));
        service.complete(ticket(), TaskOutcome.CANCELED);
        verify(claims, never()).releaseOwned("ws", "i");
    }

    private static InstanceEntity instance(TaskInstanceStatus status) {
        InstanceEntity instance = new InstanceEntity();
        instance.setId("i");
        instance.setWorkspaceId("ws");
        instance.setDefinitionId("d");
        instance.setDefinitionVersion(1);
        instance.setTaskType("DATA_SYNC");
        instance.setResourceRevision("r");
        instance.setStatus(status);
        return instance;
    }

    private static DispatchTicket ticket() {
        return new DispatchTicket("ws", "i", "d", 1, "DATA_SYNC", "r", "token-1", false);
    }

    private void inject(String fieldName, Object dependency) {
        try {
            Field field = InstanceLifecycleServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
