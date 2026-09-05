import type { WorkflowNodeInstance } from '@/services/workflow';
import type { WorkflowNodeRuntimeState } from './types';

/** 与 yak-workflow-engine NodeExecutionStatus.isActive() 保持一致。 */
export const WORKFLOW_ACTIVE_NODE_STATUSES = new Set([
  'READY',
  'SUBMITTED',
  'RUNNING',
  'PAUSING',
  'PAUSED',
  'RESUMING',
]);

export const WORKFLOW_TERMINAL_NODE_STATUSES = new Set([
  'SUCCESS',
  'SUCCESS_WITH_WARNINGS',
  'FAILED',
  'UPSTREAM_FAILED',
  'WARNING',
  'CANCELED',
  'TIMED_OUT',
  'SKIPPED',
]);

export const isWorkflowNodeActive = (status?: string) =>
  Boolean(status && WORKFLOW_ACTIVE_NODE_STATUSES.has(status));

export const isWorkflowNodeSuccessful = (status?: string) =>
  status === 'SUCCESS' || status === 'SUCCESS_WITH_WARNINGS';

const timestamp = (value?: string) => {
  if (!value) return undefined;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : undefined;
};

export const workflowNodeRuntimeState = (
  node: WorkflowNodeInstance,
): WorkflowNodeRuntimeState => {
  const attempt = node.attempts[node.attempts.length - 1];
  const startedAt = attempt?.startedAt;
  const endedAt = attempt?.endedAt;
  const startMillis = timestamp(startedAt);
  const endMillis = timestamp(endedAt);

  return {
    status: node.status,
    errorMessage: node.errorMessage || attempt?.errorMessage,
    failureReason: node.failureReason || attempt?.failureReason,
    attemptCount: node.attemptCount,
    currentAttemptNumber: node.currentAttemptNumber,
    startedAt,
    endedAt,
    elapsedMillis:
      startMillis !== undefined && endMillis !== undefined
        ? Math.max(0, endMillis - startMillis)
        : undefined,
  };
};

const RUNTIME_STATUS_MESSAGE_IDS: Record<string, string> = {
  WAITING: 'pages.workflow.editor.runtime.waiting',
  READY: 'pages.workflow.editor.runtime.ready',
  SUBMITTED: 'pages.workflow.editor.runtime.submitted',
  RUNNING: 'pages.workflow.editor.runtime.running',
  PAUSING: 'pages.workflow.editor.runtime.pausing',
  PAUSED: 'pages.workflow.editor.runtime.paused',
  RESUMING: 'pages.workflow.editor.runtime.resuming',
  SUCCESS: 'pages.workflow.editor.runtime.success',
  SUCCESS_WITH_WARNINGS: 'pages.workflow.editor.runtime.successWithWarnings',
  WARNING: 'pages.workflow.editor.runtime.successWithWarnings',
  FAILED: 'pages.workflow.editor.runtime.failed',
  UPSTREAM_FAILED: 'pages.workflow.editor.runtime.upstreamFailed',
  TIMED_OUT: 'pages.workflow.editor.runtime.timedOut',
  CANCELED: 'pages.workflow.editor.runtime.canceled',
  SKIPPED: 'pages.workflow.editor.runtime.skipped',
};

export const runtimeStatusMessageId = (status?: string) =>
  status ? RUNTIME_STATUS_MESSAGE_IDS[status] : undefined;

export const formatRuntimeDuration = (elapsedMillis?: number) => {
  if (elapsedMillis === undefined) return undefined;
  if (elapsedMillis < 1000) return `${elapsedMillis}ms`;
  if (elapsedMillis < 60_000) return `${(elapsedMillis / 1000).toFixed(elapsedMillis < 10_000 ? 1 : 0)}s`;
  const minutes = Math.floor(elapsedMillis / 60_000);
  const seconds = Math.round((elapsedMillis % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
};
