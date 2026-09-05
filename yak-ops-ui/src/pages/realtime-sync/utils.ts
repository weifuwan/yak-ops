import type {
  ComputeEnvironmentOption,
  RealtimeAction,
  RealtimeJob,
  RealtimePageQuery,
} from '@/services/realtime-sync';

import type {
  RealtimeFilterState,
  RealtimePaginationState,
} from './types';

export interface RealtimeStatusStyle {
  dot: string;
  text: string;
  background: string;
  border: string;
}

export const getRealtimeStatusStyle = (
  state?: string,
): RealtimeStatusStyle => {
  switch (String(state || '').toUpperCase()) {
    case 'RUNNING':
    case 'PUBLISHED':
      return {
        dot: '#12b76a',
        text: '#027a48',
        background: '#ecfdf3',
        border: '#abefc6',
      };
    case 'STARTING':
    case 'STOPPING':
      return {
        dot: '#2e90fa',
        text: '#175cd3',
        background: '#eff8ff',
        border: '#b2ddff',
      };
    case 'FAILED':
    case 'CONFLICT':
      return {
        dot: '#f04438',
        text: '#b42318',
        background: '#fef3f2',
        border: '#fecdca',
      };
    case 'UNKNOWN':
      return {
        dot: '#f79009',
        text: '#b54708',
        background: '#fffaeb',
        border: '#fedf89',
      };
    case 'DRAFT':
    case 'STOPPED':
    default:
      return {
        dot: '#98a2b3',
        text: '#475467',
        background: '#f9fafb',
        border: '#eaecf0',
      };
  }
};

export const isValidRealtimeTaskId = (value?: string) =>
  !value?.trim() || /^\d+$/.test(value.trim());

export const buildRealtimePageQuery = (
  filters: RealtimeFilterState,
  pagination: Pick<RealtimePaginationState, 'current' | 'pageSize'>,
): RealtimePageQuery => {
  const id = filters.id?.trim();
  return {
    pageNo: pagination.current,
    pageSize: pagination.pageSize,
    keyword: filters.keyword?.trim() || undefined,
    id: id && /^\d+$/.test(id) ? Number(id) : undefined,
    releaseState: filters.releaseState,
    stateGroup:
      filters.stateGroup === 'ALL' ? undefined : filters.stateGroup,
  };
};

export const getRealtimeEditPath = (job: Pick<RealtimeJob, 'id'>) =>
  `/sync/realtime/${encodeURIComponent(String(job.id))}/detail?scene=edit`;

export const preferredRealtimeEnvironmentId = (
  environments: ComputeEnvironmentOption[],
) =>
  environments.find(
    (item) => item.defaultEnvironment && item.enabled,
  )?.id ?? environments.find((item) => item.enabled)?.id;

export const createsRealtimeExecution = (action: RealtimeAction) =>
  action === 'start' ||
  action === 'restart-execution' ||
  action === 'apply-published-version';

export const isRealtimeStableRunning = (job: RealtimeJob) =>
  job.desiredState === 'RUNNING' && job.observedState === 'RUNNING';

export const isRealtimeReconciliationState = (state: string) =>
  ['UNKNOWN', 'CONFLICT', 'STARTING', 'STOPPING'].includes(state);

export type RealtimeStartUnavailableReason =
  | 'ENVIRONMENT_DISABLED'
  | 'NO_PUBLISHED_VERSION'
  | 'ALREADY_RUNNING'
  | 'DRAFT_NOT_PUBLISHED';

export interface RealtimeStartAvailability {
  disabled: boolean;
  reason?: RealtimeStartUnavailableReason;
  environmentName?: string;
  publishedVersion?: number;
}

export const getRealtimeStartAvailability = (
  job: RealtimeJob,
  environment?: ComputeEnvironmentOption,
): RealtimeStartAvailability => {
  const running = job.desiredState === 'RUNNING';
  const hasPublishedVersion = job.publishedVersion != null;
  const currentDraftIsPublished =
    job.releaseState === 'PUBLISHED' &&
    job.publishedVersion === job.definitionVersion;
  const environmentDisabled =
    currentDraftIsPublished && environment?.enabled === false;

  if (environmentDisabled) {
    return {
      disabled: true,
      reason: 'ENVIRONMENT_DISABLED',
      environmentName: environment?.name,
    };
  }
  if (!hasPublishedVersion) {
    return { disabled: true, reason: 'NO_PUBLISHED_VERSION' };
  }
  if (running) {
    return { disabled: true, reason: 'ALREADY_RUNNING' };
  }
  if (!currentDraftIsPublished) {
    return {
      disabled: false,
      reason: 'DRAFT_NOT_PUBLISHED',
      publishedVersion: job.publishedVersion,
    };
  }
  return { disabled: false };
};

export const copyRealtimeText = async (value: string | number) => {
  const text = String(value);
  if (
    typeof navigator !== 'undefined' &&
    navigator.clipboard &&
    typeof window !== 'undefined' &&
    window.isSecureContext
  ) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  document.execCommand('copy');
  document.body.removeChild(textarea);
};
