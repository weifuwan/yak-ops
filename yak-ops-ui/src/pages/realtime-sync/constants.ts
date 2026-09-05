import type { ThemeConfig } from 'antd';

import type {
  RealtimeFilterState,
  RealtimePaginationState,
} from './types';

interface IntlFormatter {
  formatMessage: (descriptor: { id: string }) => string;
}

export const REALTIME_SYNC_INITIAL_FILTERS: RealtimeFilterState = {
  stateGroup: 'ALL',
};

export const REALTIME_SYNC_DEFAULT_PAGINATION: RealtimePaginationState = {
  current: 1,
  pageSize: 20,
  total: 0,
};

export const REALTIME_SYNC_PAGE_SIZE_OPTIONS = [10, 20, 50];

export const getRealtimeSyncStatusTabs = (intl: IntlFormatter) => [
  {
    label: intl.formatMessage({ id: 'pages.realtimeSync.status.all' }),
    value: 'ALL',
  },
  {
    label: intl.formatMessage({ id: 'pages.realtimeSync.status.running' }),
    value: 'RUNNING',
  },
  {
    label: intl.formatMessage({ id: 'pages.realtimeSync.status.stopped' }),
    value: 'STOPPED',
  },
  {
    label: intl.formatMessage({ id: 'pages.realtimeSync.status.abnormal' }),
    value: 'ABNORMAL',
  },
] as const;

export const getRealtimeSyncReleaseOptions = (intl: IntlFormatter) => [
  {
    label: intl.formatMessage({ id: 'pages.realtimeSync.status.draft' }),
    value: 'DRAFT',
  },
  {
    label: intl.formatMessage({ id: 'pages.realtimeSync.status.published' }),
    value: 'PUBLISHED',
  },
] as const;

export const getRealtimeObservedStateLabels = (
  intl: IntlFormatter,
): Record<string, string> => ({
  STOPPED: intl.formatMessage({ id: 'pages.realtimeSync.status.stopped' }),
  STARTING: intl.formatMessage({ id: 'pages.realtimeSync.status.starting' }),
  RUNNING: intl.formatMessage({ id: 'pages.realtimeSync.status.running' }),
  STOPPING: intl.formatMessage({ id: 'pages.realtimeSync.status.stopping' }),
  FAILED: intl.formatMessage({ id: 'pages.realtimeSync.status.failed' }),
  UNKNOWN: intl.formatMessage({ id: 'pages.realtimeSync.status.unknown' }),
  CONFLICT: intl.formatMessage({ id: 'pages.realtimeSync.status.conflict' }),
});

export const getRealtimeReleaseStateLabels = (
  intl: IntlFormatter,
): Record<string, string> => ({
  DRAFT: intl.formatMessage({ id: 'pages.realtimeSync.status.draft' }),
  PUBLISHED: intl.formatMessage({ id: 'pages.realtimeSync.status.published' }),
});

export const REALTIME_SYNC_FALLBACK_POLL_INTERVAL = 5000;
export const REALTIME_SYNC_START_POLL_INTERVAL = 2000;
export const REALTIME_SYNC_START_POLL_ATTEMPTS = 15;

export const REALTIME_SYNC_PAGE_THEME: ThemeConfig = {
  token: {
    borderRadius: 10,
    colorBorder: '#f0f0f0',
    colorBgContainer: '#ffffff',
  },
  components: {
    Button: { borderRadius: 8 },
    Input: { activeShadow: 'none' },
    Select: { activeOutlineColor: 'transparent' },
  },
};
