import type {
  DashboardStatusFilter,
  DashboardTimeRange,
} from './types';

export const DASHBOARD_DEFAULT_PAGE_SIZE = 10;
export const DASHBOARD_PAGE_SIZE_OPTIONS = [10, 20, 50];

export const DASHBOARD_STATUS_FILTERS: Array<{
  key: DashboardStatusFilter;
  messageId: string;
}> = [
  { key: 'all', messageId: 'pages.dashboard.list.status.all' },
  { key: 'published', messageId: 'pages.dashboard.list.status.published' },
  { key: 'draft', messageId: 'pages.dashboard.list.status.draft' },
  { key: 'unpublished', messageId: 'pages.dashboard.list.status.unpublished' },
];

export const DASHBOARD_TIME_RANGE_OPTIONS: Array<{
  value: DashboardTimeRange;
  messageId: string;
}> = [
  { value: 'all', messageId: 'pages.dashboard.list.time.all' },
  { value: '7d', messageId: 'pages.dashboard.list.time.7d' },
  { value: '30d', messageId: 'pages.dashboard.list.time.30d' },
];
