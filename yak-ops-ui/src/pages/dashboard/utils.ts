import type { DashboardSummary } from '@/services/dashboard';

import type {
  DashboardLifecycle,
  DashboardLifecycleCounts,
  DashboardListFilters,
} from './types';

const DAY_MILLISECONDS = 24 * 60 * 60 * 1000;

export const formatDashboardTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

export const formatDashboardDate = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 10) : '-';

export const dashboardTimeValue = (value?: string) => {
  if (!value) return 0;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
};

export const getDashboardLifecycle = (
  dashboard: DashboardSummary,
): DashboardLifecycle => {
  const published = Number(dashboard.publishedVersionNo) > 0;
  const hasDraft =
    published && dashboard.currentVersionId !== dashboard.publishedVersionId;

  return {
    published,
    hasDraft,
    state: !published ? 'unpublished' : hasDraft ? 'draft' : 'published',
  };
};

export const countDashboardLifecycles = (
  dashboards: DashboardSummary[],
): DashboardLifecycleCounts =>
  dashboards.reduce<DashboardLifecycleCounts>(
    (result, dashboard) => {
      result[getDashboardLifecycle(dashboard).state] += 1;
      return result;
    },
    { published: 0, draft: 0, unpublished: 0 },
  );

export const filterDashboardSummaries = (
  dashboards: DashboardSummary[],
  filters: DashboardListFilters,
  now = Date.now(),
): DashboardSummary[] => {
  const keyword = filters.keyword.trim().toLowerCase();
  const days = filters.timeRange === '7d' ? 7 : 30;

  return dashboards.filter((dashboard) => {
    if (
      keyword &&
      ![dashboard.name, dashboard.description, dashboard.id].some((field) =>
        String(field || '')
          .toLowerCase()
          .includes(keyword),
      )
    ) {
      return false;
    }

    if (
      filters.status !== 'all' &&
      getDashboardLifecycle(dashboard).state !== filters.status
    ) {
      return false;
    }

    if (filters.timeRange !== 'all') {
      const updateTime = dashboardTimeValue(dashboard.updateTime);
      if (!updateTime || now - updateTime > days * DAY_MILLISECONDS) {
        return false;
      }
    }

    return true;
  });
};

export const paginateDashboardSummaries = (
  dashboards: DashboardSummary[],
  page: number,
  pageSize: number,
) => {
  const pageCount = Math.max(1, Math.ceil(dashboards.length / pageSize));
  const currentPage = Math.min(Math.max(1, page), pageCount);
  const start = (currentPage - 1) * pageSize;
  return {
    pageCount,
    currentPage,
    records: dashboards.slice(start, start + pageSize),
  };
};

export const dashboardOpenPath = (dashboard: DashboardSummary) =>
  getDashboardLifecycle(dashboard).published
    ? `/dashboard/${encodeURIComponent(dashboard.id)}`
    : `/dashboard/${encodeURIComponent(dashboard.id)}/edit`;

export const dashboardEditPath = (dashboardId: string) =>
  `/dashboard/${encodeURIComponent(dashboardId)}/edit`;

export const dashboardLifecycleMessage = (dashboard: DashboardSummary) => {
  const lifecycle = getDashboardLifecycle(dashboard);
  if (lifecycle.state === 'published') {
    return {
      id: 'pages.dashboard.list.lifecycle.published',
      values: { version: dashboard.publishedVersionNo },
    };
  }
  if (lifecycle.state === 'draft') {
    return {
      id: 'pages.dashboard.list.lifecycle.draft',
      values: { version: dashboard.currentVersionNo },
    };
  }
  return {
    id: 'pages.dashboard.list.lifecycle.unpublished',
    values: { version: dashboard.currentVersionNo },
  };
};

export const dashboardLifecycleClassName = (dashboard: DashboardSummary) => {
  const state = getDashboardLifecycle(dashboard).state;
  if (state === 'published') return 'text-[#20a464]';
  if (state === 'draft') return 'text-[#667085]';
  return 'text-[#98a2b3]';
};
