import type { DashboardSummary } from '@/services/dashboard';

import {
  countDashboardLifecycles,
  dashboardEditPath,
  dashboardLifecycleMessage,
  dashboardOpenPath,
  filterDashboardSummaries,
  getDashboardLifecycle,
  paginateDashboardSummaries,
} from './utils';

const dashboard = (
  value: Partial<DashboardSummary> & Pick<DashboardSummary, 'id' | 'name'>,
): DashboardSummary => ({
  id: value.id,
  name: value.name,
  description: value.description || '',
  currentVersionId: value.currentVersionId,
  currentVersionNo: value.currentVersionNo ?? 1,
  publishedVersionId: value.publishedVersionId,
  publishedVersionNo: value.publishedVersionNo ?? 0,
  publishedTime: value.publishedTime,
  createTime: value.createTime,
  updateTime: value.updateTime,
});

const records = [
  dashboard({
    id: '1',
    name: '销售看板',
    description: '订单与收入',
    currentVersionId: 'v1',
    publishedVersionId: 'v1',
    publishedVersionNo: 1,
    updateTime: '2026-08-25T10:00:00',
  }),
  dashboard({
    id: '2',
    name: '库存看板',
    description: '仓库库存',
    currentVersionId: 'v3',
    publishedVersionId: 'v2',
    currentVersionNo: 3,
    publishedVersionNo: 2,
    updateTime: '2026-08-20T10:00:00',
  }),
  dashboard({
    id: '3',
    name: '客户分析',
    description: '用户分层',
    currentVersionId: 'v1',
    currentVersionNo: 1,
    updateTime: '2026-06-01T10:00:00',
  }),
];

describe('dashboard list utils', () => {
  it('distinguishes published, draft and unpublished lifecycle states', () => {
    expect(getDashboardLifecycle(records[0]).state).toBe('published');
    expect(getDashboardLifecycle(records[1]).state).toBe('draft');
    expect(getDashboardLifecycle(records[2]).state).toBe('unpublished');
    expect(countDashboardLifecycles(records)).toEqual({
      published: 1,
      draft: 1,
      unpublished: 1,
    });
  });

  it('filters by keyword and lifecycle', () => {
    expect(
      filterDashboardSummaries(records, {
        keyword: '库存',
        status: 'draft',
        timeRange: 'all',
      }),
    ).toEqual([records[1]]);
  });

  it('filters by recent update range', () => {
    const now = new Date('2026-08-26T10:00:00').getTime();
    expect(
      filterDashboardSummaries(
        records,
        { keyword: '', status: 'all', timeRange: '7d' },
        now,
      ).map((item) => item.id),
    ).toEqual(['1', '2']);
  });

  it('clamps pagination to the final available page', () => {
    expect(paginateDashboardSummaries(records, 9, 2)).toEqual({
      pageCount: 2,
      currentPage: 2,
      records: [records[2]],
    });
  });

  it('opens unpublished dashboards in editor and published dashboards in reader', () => {
    expect(dashboardOpenPath(records[0])).toBe('/dashboard/1');
    expect(dashboardOpenPath(records[2])).toBe('/dashboard/3/edit');
    expect(dashboardEditPath('id/a')).toBe('/dashboard/id%2Fa/edit');
  });

  it('builds locale-neutral lifecycle message descriptors from version semantics', () => {
    expect(dashboardLifecycleMessage(records[0])).toEqual({
      id: 'pages.dashboard.list.lifecycle.published',
      values: { version: 1 },
    });
    expect(dashboardLifecycleMessage(records[1])).toEqual({
      id: 'pages.dashboard.list.lifecycle.draft',
      values: { version: 3 },
    });
    expect(dashboardLifecycleMessage(records[2])).toEqual({
      id: 'pages.dashboard.list.lifecycle.unpublished',
      values: { version: 1 },
    });
  });
});
