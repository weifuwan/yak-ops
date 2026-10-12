package io.yak.ops.business.task.execution.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.execution.DispatchTicket;
import io.yak.ops.business.task.execution.PendingRun;
import io.yak.ops.common.enums.task.TaskAttemptStatus;
import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.ExecutionClaimRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies transactional dispatch fencing and terminal claim release semantics. */
class ExecutionLifecycleServiceTest {

    private final ExecutionLifecycleServiceImpl service = new ExecutionLifecycleServiceImpl();
    private final InstanceRepository instances = mock(InstanceRepository.class);
    private final AttemptRepository attempts = mock(AttemptRepository.class);
    private final ExecutionClaimRepository claims = mock(ExecutionClaimRepository.class);

    ExecutionLifecycleServiceTest() {
        inject("instances", instances);
        inject("attempts", attempts);
        inject("claims", claims);
    }

    @Test
    void claimsExactlyOnePendingAttempt() {
        InstanceEntity instance = instance(TaskInstanceStatus.SUBMITTED);
        AttemptEntity attempt = attempt(TaskAttemptStatus.PENDING);
        when(instances.findByIdForUpdate("ws", "i")).thenReturn(Optional.of(instance));
        when(attempts.findByInstance("ws", "i")).thenReturn(List.of(attempt));
        when(attempts.claimPending("ws", "a", "owner-1", "token-1")).thenReturn(true);

        DispatchTicket ticket = service.claim("ws", "i", "owner-1", "token-1");
        assertEquals("a", ticket.attemptId());
        assertEquals("token-1", ticket.dispatchToken());
        assertNull(service.claim("ws", "i", "owner-1", "token-2"));
        verify(attempts).claimPending("ws", "a", "owner-1", "token-2");
    }

    @Test
    void cancelBeforeDispatchTerminatesAndReleasesClaims() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.SUBMITTED)));
        when(attempts.findByInstance("ws", "i"))
                .thenReturn(List.of(attempt(TaskAttemptStatus.PENDING)));
        when(attempts.cancelPending("ws", "a")).thenReturn(true);
        when(instances.markTerminal("ws", "i", TaskInstanceStatus.CANCELED, null)).thenReturn(true);

        assertEquals(TaskInstanceStatus.CANCELED, service.cancel("ws", "i"));
        verify(claims).releaseOwned("ws", "i");
        verify(instances, never()).markCancelling("ws", "i");
    }

    @Test
    void terminalExecutionIsRecordedWithoutRestartingAnAttempt() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));
        when(attempts.markTerminal("ws", "a", "token-1", TaskAttemptStatus.FINISHED, null))
                .thenReturn(true);
        when(instances.markTerminal("ws", "i", TaskInstanceStatus.FINISHED, null))
                .thenReturn(true);

        service.complete(ticket(), TaskOutcome.FINISHED);

        verify(claims).releaseOwned("ws", "i");
    }

    @Test
    void pendingSubmissionRemainsRecoverableAfterHttpResponseIsLost() {
        when(attempts.pending()).thenReturn(List.of(attempt(TaskAttemptStatus.PENDING)));
        when(instances.findById("ws", "i")).thenReturn(Optional.of(instance(TaskInstanceStatus.SUBMITTED)));

        assertEquals(List.of(new PendingRun("ws", "i")), service.pendingRuns());
        verify(attempts, never()).claimPending("ws", "a", "owner-1", "token-1");
    }

    @Test
    void uncertainOutcomeRetainsClaims() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));

        service.uncertain(ticket());

        verify(instances).markReconciling("ws", "i");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void cancelAfterLaunchRetainsClaimsUntilPhysicalCompletion() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));
        when(attempts.findByInstance("ws", "i"))
                .thenReturn(List.of(attempt(TaskAttemptStatus.RUNNING)));
        when(instances.findById("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.CANCELLING)));

        assertEquals(TaskInstanceStatus.CANCELLING, service.cancel("ws", "i"));

        verify(instances).markCancelling("ws", "i");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void staleTerminalCallbackCannotReleaseAnotherLaunchersResources() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));

        assertThrows(IllegalStateException.class, () -> service.complete(ticket(), TaskOutcome.FINISHED));

        verify(claims, never()).releaseOwned("ws", "i");
        verify(instances, never()).markTerminal("ws", "i", TaskInstanceStatus.FINISHED, null);
    }

    @Test
    void startupDoesNotReplayActiveAttemptFromPreviousOwner() {
        AttemptEntity previous = attempt(TaskAttemptStatus.RUNNING);
        previous.setOwnerIncarnation("previous-owner");
        when(attempts.activeByOtherOwner("new-owner")).thenReturn(List.of(previous));
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.RUNNING)));

        service.reconcilePrevious("new-owner");

        verify(instances).markReconciling("ws", "i");
        verify(attempts, never()).claimPending("ws", "a", "new-owner", "new-token");
        verify(claims, never()).releaseOwned("ws", "i");
    }

    @Test
    void repeatedTerminalNotificationPreservesImmutableOutcome() {
        when(instances.findByIdForUpdate("ws", "i"))
                .thenReturn(Optional.of(instance(TaskInstanceStatus.FINISHED)));

        service.complete(ticket(), TaskOutcome.CANCELED);

        verify(claims, never()).releaseOwned("ws", "i");
        verify(attempts, never())
                .markTerminal("ws", "a", "token-1", TaskAttemptStatus.CANCELED, null);
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

    private static AttemptEntity attempt(TaskAttemptStatus status) {
        AttemptEntity attempt = new AttemptEntity();
        attempt.setId("a");
        attempt.setWorkspaceId("ws");
        attempt.setInstanceId("i");
        attempt.setAttemptNumber(1);
        attempt.setStatus(status);
        return attempt;
    }

    private static DispatchTicket ticket() {
        return new DispatchTicket("ws", "i", "a", "d", 1, "DATA_SYNC", "r", "token-1");
    }

    private void inject(String fieldName, Object dependency) {
        try {
            Field field = ExecutionLifecycleServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
