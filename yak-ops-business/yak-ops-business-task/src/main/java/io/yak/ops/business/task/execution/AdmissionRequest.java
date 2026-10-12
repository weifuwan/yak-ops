package io.yak.ops.business.task.execution;

import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;

/**
 * Prepared, read-only inputs of one Task admission transaction.
 *
 * @param context trusted execution identity and pinned Definition version
 * @param requestId caller-stable idempotency key
 * @param requestFingerprint non-secret submission intent fingerprint
 * @param operatorId actor initiating the execution
 * @param parameters validated immutable Task parameters
 * @param plan resolved execution resources and binding revision
 * @param plugin executable Task type used to prepare the plan
 */
public record AdmissionRequest(
        TaskExecutionContext context,
        String requestId,
        String requestFingerprint,
        String operatorId,
        TaskParameters parameters,
        TaskExecutionPlan plan,
        ExecutableTaskPlugin plugin) {}
