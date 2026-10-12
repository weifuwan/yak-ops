package io.yak.ops.business.task.execution.impl;

import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.execution.ExecutableTaskPlugin;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;

/** Prepared, read-only execution admission input internal to Task business. */
record AdmissionRequest(
        TaskExecutionContext context,
        String requestId,
        String requestFingerprint,
        String operatorId,
        TaskParameters parameters,
        TaskExecutionPlan plan,
        ExecutableTaskPlugin plugin) {}
