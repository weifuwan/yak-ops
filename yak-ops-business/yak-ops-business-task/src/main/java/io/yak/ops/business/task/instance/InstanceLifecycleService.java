package io.yak.ops.business.task.instance;

import io.yak.ops.common.enums.task.TaskInstanceStatus;
import io.yak.ops.plugin.task.api.execution.TaskOutcome;
import io.yak.ops.plugin.task.api.execution.TaskRuntimeReference;
import java.util.List;

/** Owns transactional launch arbitration, cancellation intent and business outcomes. */
public interface InstanceLifecycleService {

    /**
     * Acquires one committed PENDING Attempt under the locked Instance.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId logical run
     * @param ownerIncarnation verified embedded process identity
     * @param dispatchToken unique opaque token for this launch
     * @return claimed dispatch identity, or null if admission has already been canceled or launched
     */
    DispatchTicket claim(String workspaceId, String instanceId, String ownerIncarnation, String dispatchToken);

    /**
     * Records Job identity without reviving terminal or cancelling Instances.
     *
     * @param ticket winning dispatcher
     * @param reference engine-provided Job identity
     */
    void started(DispatchTicket ticket, TaskRuntimeReference reference);

    /**
     * Persists an authoritative completed physical execution and releases its claims.
     *
     * @param ticket winning dispatcher
     * @param outcome confirmed Job outcome
     */
    void complete(DispatchTicket ticket, TaskOutcome outcome);

    /**
     * Records an execution that cannot safely be resubmitted or released.
     *
     * @param ticket winning dispatcher
     */
    void uncertain(DispatchTicket ticket);

    /**
     * Atomically closes a pending run, or persists cancellation intent for launched work.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId logical run
     * @return updated, authoritative business Instance status
     */
    TaskInstanceStatus cancel(String workspaceId, String instanceId);

    /**
     * Returns committed but unclaimed submissions that can be safely dispatched after startup.
     *
     * @return Workspace-scoped identities of runnable PENDING Instances
     */
    List<PendingRun> pendingRuns();

    /** @param currentOwner current embedded JVM incarnation that has obtained the OS process lock */
    void reconcilePrevious(String currentOwner);
}
