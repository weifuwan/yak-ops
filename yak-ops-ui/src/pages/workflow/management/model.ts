import type {
  WorkflowDefinition,
  WorkflowDefinitionStatus,
} from '@/services/workflow/definitions';

export type WorkflowFilterKey = 'ALL' | WorkflowDefinitionStatus;
export type WorkflowViewMode = 'grid' | 'list';

export interface WorkflowSummary {
  total: number;
  online: number;
  draftChanged: number;
  activeExecutions: number;
}

export const WORKFLOW_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const WORKFLOW_STATUS_TABS: Array<{
  key: WorkflowFilterKey;
  messageId: string;
}> = [
  { key: 'ALL', messageId: 'pages.workflow.definition.filter.all' },
  { key: 'ONLINE', messageId: 'pages.workflow.definition.filter.online' },
  { key: 'DRAFT', messageId: 'pages.workflow.definition.filter.draft' },
  { key: 'OFFLINE', messageId: 'pages.workflow.definition.filter.offline' },
];

const ACTIVE_RUNTIME_STATUSES = new Set([
  'CREATED',
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
  'PAUSING',
  'PAUSED',
  'RESUMING',
]);

const RUNNING_RUNTIME_STATUSES = new Set([
  'CREATED',
  'WAITING',
  'READY',
  'SUBMITTED',
  'RUNNING',
]);

const RUNTIME_MESSAGE_IDS: Record<string, string> = {
  CREATED: 'pages.workflow.status.runtime.created',
  WAITING: 'pages.workflow.status.runtime.waiting',
  READY: 'pages.workflow.status.runtime.ready',
  SUBMITTED: 'pages.workflow.status.runtime.submitted',
  RUNNING: 'pages.workflow.status.runtime.running',
  PAUSING: 'pages.workflow.status.runtime.pausing',
  PAUSED: 'pages.workflow.status.runtime.paused',
  RESUMING: 'pages.workflow.status.runtime.resuming',
  SUCCESS: 'pages.workflow.status.runtime.success',
  SUCCESS_WITH_WARNINGS: 'pages.workflow.status.runtime.successWithWarnings',
  FAILED: 'pages.workflow.status.runtime.failed',
  WARNING: 'pages.workflow.status.runtime.warning',
  CANCELED: 'pages.workflow.status.runtime.canceled',
  TIMED_OUT: 'pages.workflow.status.runtime.timedOut',
};

const FAILURE_STRATEGY_MESSAGE_IDS: Record<string, string> = {
  FAIL_FAST: 'pages.workflow.editor.toolbar.failure.fast',
  CONTINUE_INDEPENDENT_BRANCHES: 'pages.workflow.editor.toolbar.failure.continue',
  TERMINATE_ALL: 'pages.workflow.editor.toolbar.failure.terminate',
};

export const DEFINITION_STATUS_META: Record<
  WorkflowDefinitionStatus,
  { messageId: string; textClassName: string; backgroundClassName: string }
> = {
  DRAFT: {
    messageId: 'pages.workflow.status.definition.draft',
    textClassName: 'text-[#667085]',
    backgroundClassName: 'bg-[#f1f3f5]',
  },
  ONLINE: {
    messageId: 'pages.workflow.status.definition.online',
    textClassName: 'text-[#e5254e]',
    backgroundClassName: 'bg-[#fff1f4]',
  },
  OFFLINE: {
    messageId: 'pages.workflow.status.definition.offline',
    textClassName: 'text-[#667085]',
    backgroundClassName: 'bg-[#f1f3f5]',
  },
};

export const isActiveRuntime = (status?: string) =>
  Boolean(status && ACTIVE_RUNTIME_STATUSES.has(status));

export const isRunningRuntime = (status?: string) =>
  Boolean(status && RUNNING_RUNTIME_STATUSES.has(status));

export const runtimeStatusMeta = (status?: string) => {
  const labelId = status
    ? RUNTIME_MESSAGE_IDS[status]
    : 'pages.workflow.status.runtime.neverRun';

  if (!status) {
    return {
      labelId,
      rawLabel: undefined,
      dotClassName: 'bg-[#a6abb4]',
      textClassName: 'text-[#777d88]',
      backgroundClassName: 'bg-[#f3f4f6]',
    };
  }

  if (status === 'FAILED' || status === 'TIMED_OUT') {
    return {
      labelId,
      rawLabel: labelId ? undefined : status,
      dotClassName: 'bg-[#e45863]',
      textClassName: 'text-[#c74350]',
      backgroundClassName: 'bg-[#fff1f2]',
    };
  }

  if (status === 'WARNING' || status === 'SUCCESS_WITH_WARNINGS') {
    return {
      labelId,
      rawLabel: labelId ? undefined : status,
      dotClassName: 'bg-[#e39b35]',
      textClassName: 'text-[#b77a22]',
      backgroundClassName: 'bg-[#fff7e9]',
    };
  }

  if (isActiveRuntime(status)) {
    return {
      labelId,
      rawLabel: labelId ? undefined : status,
      dotClassName: 'bg-[#fe2c55]',
      textClassName: 'text-[#e5254e]',
      backgroundClassName: 'bg-[#fff1f4]',
    };
  }

  if (status === 'SUCCESS') {
    return {
      labelId,
      rawLabel: undefined,
      dotClassName: 'bg-[#38a169]',
      textClassName: 'text-[#36845d]',
      backgroundClassName: 'bg-[#edf8f1]',
    };
  }

  return {
    labelId,
    rawLabel: labelId ? undefined : status,
    dotClassName: 'bg-[#8f96a3]',
    textClassName: 'text-[#667085]',
    backgroundClassName: 'bg-[#f3f4f6]',
  };
};

export const getPublishActionMessageId = (record: WorkflowDefinition) => {
  if (record.status === 'ONLINE') return 'pages.workflow.definition.offlineTitle';
  if (
    record.status === 'OFFLINE' &&
    record.activeVersionNo &&
    !record.draftChanged
  ) {
    return 'pages.workflow.definition.reenable';
  }
  if (record.activeVersionNo && record.draftChanged) {
    return 'pages.workflow.definition.publishUpdate';
  }
  return 'pages.workflow.definition.publish';
};

export const formatWorkflowTime = (value?: string, locale?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(locale);
};

export const formatWorkflowDuration = (
  seconds: number | undefined,
  formatMessage: (
    descriptor: { id: string },
    values?: Record<string, string | number>,
  ) => string,
) => {
  if (!seconds || seconds <= 0) {
    return formatMessage({ id: 'pages.workflow.common.notSet' });
  }
  if (seconds < 60) {
    return formatMessage(
      { id: 'pages.workflow.common.seconds' },
      { value: seconds },
    );
  }
  if (seconds % 3600 === 0) {
    return formatMessage(
      { id: 'pages.workflow.common.hours' },
      { value: seconds / 3600 },
    );
  }
  if (seconds % 60 === 0) {
    return formatMessage(
      { id: 'pages.workflow.common.minutes' },
      { value: seconds / 60 },
    );
  }
  return formatMessage(
    { id: 'pages.workflow.common.seconds' },
    { value: seconds },
  );
};

export const failureStrategyMessageId = (value: string) =>
  FAILURE_STRATEGY_MESSAGE_IDS[value];

export const buildWorkflowSummary = (
  definitions: WorkflowDefinition[],
): WorkflowSummary => ({
  total: definitions.length,
  online: definitions.filter((item) => item.status === 'ONLINE').length,
  draftChanged: definitions.filter((item) => item.draftChanged).length,
  activeExecutions: definitions.filter((item) =>
    isActiveRuntime(item.latestExecutionStatus),
  ).length,
});

export const WORKFLOW_PAGE_ANIMATION = {
  fadeUp: {
    hidden: { opacity: 0, y: 18 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.45,
        ease: [0.22, 1, 0.36, 1],
      },
    },
  },
  sectionStagger: {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: 0.08,
        delayChildren: 0.06,
      },
    },
  },
  cardStagger: {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: 0.06,
      },
    },
  },
};
