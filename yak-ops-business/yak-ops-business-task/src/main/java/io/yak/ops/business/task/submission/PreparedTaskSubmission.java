package io.yak.ops.business.task.submission;

import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import java.time.Instant;

/**
 * Validated inputs for one atomic Task Instance submission.
 *
 * <p>The executable plugin is a live capability used for read-only plan revalidation
 * before submission. This request holds that capability without invoking or owning its
 * lifecycle and therefore has identity semantics rather than value equality.
 */
public final class PreparedTaskSubmission {

    private final TaskExecutionIdentity identity;
    private final boolean debug;
    private final TaskTriggerType triggerType;
    private final String requestId;
    private final String requestFingerprint;
    private final String operatorId;
    private final ReadonlyConfig parameters;
    private final TaskExecutionResources plan;
    private final TaskChannel plugin;
    private final String scheduleId;
    private final Long scheduleRevision;
    private final Instant scheduledTime;

    /**
     * Creates the inputs of a validated Task submission.
     *
     * @param identity trusted execution identity and pinned Definition version
     * @param debug diagnostics setting captured from the published Definition
     * @param triggerType namespace of the idempotent submission
     * @param requestId caller-stable idempotency key
     * @param requestFingerprint non-secret submission intent fingerprint
     * @param operatorId actor initiating execution
     * @param parameters validated immutable Task parameters
     * @param plan resolved exclusive resources and binding revision
     * @param plugin executable Task type used to prepare and revalidate the plan
     * @param scheduleId originating schedule or null
     * @param scheduleRevision originating revision or null
     * @param scheduledTime logical UTC time or null
     */
    public PreparedTaskSubmission(
            TaskExecutionIdentity identity,
            boolean debug,
            TaskTriggerType triggerType,
            String requestId,
            String requestFingerprint,
            String operatorId,
            ReadonlyConfig parameters,
            TaskExecutionResources plan,
            TaskChannel plugin,
            String scheduleId,
            Long scheduleRevision,
            Instant scheduledTime) {
        this.identity = identity;
        this.debug = debug;
        this.triggerType = triggerType;
        this.requestId = requestId;
        this.requestFingerprint = requestFingerprint;
        this.operatorId = operatorId;
        this.parameters = parameters;
        this.plan = plan;
        this.plugin = plugin;
        this.scheduleId = scheduleId;
        this.scheduleRevision = scheduleRevision;
        this.scheduledTime = scheduledTime;
    }

    /** Returns the pinned execution identity. */
    public TaskExecutionIdentity identity() {
        return identity;
    }

    /** Returns the diagnostics setting pinned with the published Definition version. */
    public boolean debug() {
        return debug;
    }

    /** Returns the idempotent submission trigger. */
    public TaskTriggerType triggerType() {
        return triggerType;
    }

    /** Returns the idempotency key associated with the submission. */
    public String requestId() {
        return requestId;
    }

    /** Returns the submission intent fingerprint. */
    public String requestFingerprint() {
        return requestFingerprint;
    }

    /** Returns the actor initiating this submission. */
    public String operatorId() {
        return operatorId;
    }

    /** Returns the validated Task parameters. */
    public ReadonlyConfig parameters() {
        return parameters;
    }

    /** Returns the admitted resource plan. */
    public TaskExecutionResources plan() {
        return plan;
    }

    /** Returns the executable plugin used for plan revalidation. */
    public TaskChannel plugin() {
        return plugin;
    }

    /** @return originating schedule ID or null */
    public String scheduleId() {
        return scheduleId;
    }

    /** @return registered schedule revision or null */
    public Long scheduleRevision() {
        return scheduleRevision;
    }

    /** @return logical UTC occurrence or null */
    public Instant scheduledTime() {
        return scheduledTime;
    }

    /** Omits plugin, parameters, and submission identifiers from diagnostics. */
    @Override
    public String toString() {
        return "PreparedTaskSubmission{contents=redacted}";
    }
}
