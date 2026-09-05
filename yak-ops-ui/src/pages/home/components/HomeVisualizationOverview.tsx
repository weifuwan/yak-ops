import { listDigitalScreens, type DigitalScreenInstance } from '@/services/digital-screen';
import {
  fetchDashboardOverview,
  type DashboardOverview,
  type DashboardSummary,
} from '@/services/dashboard';
import { history, useIntl } from '@umijs/max';
import { Clock3, LayoutDashboard, Monitor } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';
import {
  formatMetric,
  relativeTime,
  SectionHeader,
} from './homeAssetOverviewShared';

interface VisualizationState {
  dashboard?: DashboardOverview;
  screens?: DigitalScreenInstance[];
  dashboardLoading: boolean;
  screenLoading: boolean;
  dashboardFailed: boolean;
  screenFailed: boolean;
}

type VisualizationKind = 'dashboard' | 'screen';
type VisualizationStatus = 'published' | 'draft' | 'changed';

interface VisualizationItem {
  id: string;
  kind: VisualizationKind;
  name: string;
  description: string;
  updatedAt?: string;
  status: VisualizationStatus;
  path: string;
}

const timestamp = (value?: string) => {
  if (!value) return 0;
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
};

const dashboardItem = (value: DashboardSummary): VisualizationItem => {
  const published = Boolean(value.publishedVersionId);
  const hasUnpublishedChanges =
    published && value.currentVersionNo > value.publishedVersionNo;
  return {
    id: value.id,
    kind: 'dashboard',
    name: value.name,
    description: value.description || `Dashboard · v${value.currentVersionNo}`,
    updatedAt: value.updateTime || value.publishedTime || value.createTime,
    status: hasUnpublishedChanges
      ? 'changed'
      : published
        ? 'published'
        : 'draft',
    path:
      published && !hasUnpublishedChanges
        ? `/dashboard/${value.id}`
        : `/dashboard/${value.id}/edit`,
  };
};

const screenItem = (
  value: DigitalScreenInstance,
  fallbackDescription: string,
): VisualizationItem => ({
  id: value.id,
  kind: 'screen',
  name: value.name,
  description: value.description || fallbackDescription,
  updatedAt: value.updatedAt || value.publishedAt || value.createdAt,
  status: value.status === 'published' ? 'published' : 'draft',
  path:
    value.status === 'published'
      ? `/digital-screen/${value.id}`
      : `/digital-screen/${value.id}/edit`,
});

const statusClassName = (status: VisualizationStatus) => {
  if (status === 'published') return 'bg-[#eef8f2] text-[#43815f]';
  if (status === 'changed') return 'bg-[#fff7e9] text-[#a46d25]';
  return 'bg-[#f3f4f6] text-[#7d828b]';
};

function useVisualizationOverview(): VisualizationState {
  const [state, setState] = useState<VisualizationState>({
    dashboardLoading: true,
    screenLoading: true,
    dashboardFailed: false,
    screenFailed: false,
  });

  useEffect(() => {
    let active = true;

    fetchDashboardOverview(4)
      .then((dashboard) => {
        if (!active) return;
        setState((current) => ({
          ...current,
          dashboard,
          dashboardLoading: false,
          dashboardFailed: false,
        }));
      })
      .catch(() => {
        if (!active) return;
        setState((current) => ({
          ...current,
          dashboardLoading: false,
          dashboardFailed: true,
        }));
      });

    listDigitalScreens()
      .then((screens) => {
        if (!active) return;
        setState((current) => ({
          ...current,
          screens,
          screenLoading: false,
          screenFailed: false,
        }));
      })
      .catch(() => {
        if (!active) return;
        setState((current) => ({
          ...current,
          screenLoading: false,
          screenFailed: true,
        }));
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

function OverviewMetric({
  label,
  value,
  route,
}: {
  label: string;
  value?: number;
  route: string;
}) {
  const intl = useIntl();
  return (
    <button
      type="button"
      onClick={() => history.push(route)}
      className="group min-w-0 border-0 bg-transparent px-4 py-1 text-left first:pl-0 last:pr-0"
    >
      <div className="truncate text-[11px] text-[#92969f] transition-colors group-hover:text-[#6d737d]">
        {label}
      </div>
      <strong className="mt-1 block truncate text-[24px] font-semibold tracking-[-0.6px] text-[#30343d]">
        {formatMetric(value, intl.locale)}
      </strong>
    </button>
  );
}

function VisualizationPreview({ kind }: { kind: VisualizationKind }) {
  const Icon = kind === 'dashboard' ? LayoutDashboard : Monitor;
  return (
    <div className="relative flex h-[112px] items-center justify-center overflow-hidden bg-[linear-gradient(145deg,#f4f6fb_0%,#edf1f8_100%)]">
      <div className="absolute inset-3 rounded-[10px] border border-white/90 bg-white/55 shadow-[0_5px_18px_rgba(31,35,41,0.04)]" />
      <div className="relative z-10 flex flex-col items-center gap-2 text-[#7783ad]">
        <span className="flex h-10 w-10 items-center justify-center rounded-[11px] bg-white/85 shadow-sm">
          <Icon size={19} strokeWidth={1.7} />
        </span>
        <span className="text-[9px] font-medium tracking-[0.08em] text-[#9aa1b6]">
          {kind === 'dashboard' ? 'DASHBOARD' : 'DIGITAL SCREEN'}
        </span>
      </div>
    </div>
  );
}

function VisualizationCard({ item }: { item: VisualizationItem }) {
  const intl = useIntl();
  return (
    <button
      type="button"
      onClick={() => history.push(item.path)}
      className="group overflow-hidden rounded-[14px] border border-[#eceef2] bg-white text-left transition-[box-shadow,transform,border-color] duration-200 hover:-translate-y-0.5 hover:border-[#dfe2e8] hover:shadow-[0_10px_28px_rgba(31,35,41,0.075)]"
    >
      <VisualizationPreview kind={item.kind} />
      <div className="px-4 pb-4 pt-3.5">
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <strong className="block truncate text-[13px] font-semibold text-[#373b44]">
              {item.name}
            </strong>
            <p className="mt-1 truncate text-[10px] text-[#92969f]">
              {item.description}
            </p>
          </div>
          <span
            className={`shrink-0 rounded-full px-2 py-0.5 text-[9px] ${statusClassName(item.status)}`}
          >
            {intl.formatMessage({
              id: `pages.home.visualization.status.${item.status}`,
            })}
          </span>
        </div>
        <div className="mt-3 flex items-center justify-between gap-2 text-[10px] text-[#a0a4ac]">
          <span>
            {intl.formatMessage({
              id: `pages.home.visualization.kind.${item.kind}`,
            })}
          </span>
          <span className="flex min-w-0 items-center gap-1">
            <Clock3 size={11} strokeWidth={1.8} />
            <span className="truncate">
              {relativeTime(item.updatedAt, intl.locale)}
            </span>
          </span>
        </div>
      </div>
    </button>
  );
}

export default function HomeVisualizationOverview() {
  const intl = useIntl();
  const state = useVisualizationOverview();
  const dashboard = state.dashboard;
  const screens = state.screens;
  const publishedScreens = screens?.filter((item) => item.status === 'published').length;

  const recentItems = useMemo(() => {
    const items = [
      ...(dashboard?.recentDashboards || []).map(dashboardItem),
      ...(screens || []).map((item) =>
        screenItem(
          item,
          intl.formatMessage(
            { id: 'pages.home.visualization.screenFallback' },
            { templateId: item.templateId },
          ),
        ),
      ),
    ];
    return items
      .sort((left, right) => timestamp(right.updatedAt) - timestamp(left.updatedAt))
      .slice(0, 4);
  }, [dashboard, intl.locale, screens]);

  const loading = state.dashboardLoading || state.screenLoading;
  const allFailed = state.dashboardFailed && state.screenFailed;

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-6 pt-5">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.visualization.title' })}
        description=""
      />

      <div className="mt-5 grid grid-cols-2 divide-x divide-[#eef0f3] lg:grid-cols-4">
        <OverviewMetric
          label={intl.formatMessage({ id: 'pages.home.visualization.metric.dashboard' })}
          value={state.dashboardFailed ? undefined : dashboard?.dashboardCount}
          route="/dashboard"
        />
        <OverviewMetric
          label={intl.formatMessage({
            id: 'pages.home.visualization.metric.publishedDashboard',
          })}
          value={state.dashboardFailed ? undefined : dashboard?.publishedDashboardCount}
          route="/dashboard"
        />
        <OverviewMetric
          label={intl.formatMessage({ id: 'pages.home.visualization.metric.screen' })}
          value={state.screenFailed ? undefined : screens?.length}
          route="/digital-screen"
        />
        <OverviewMetric
          label={intl.formatMessage({
            id: 'pages.home.visualization.metric.publishedScreen',
          })}
          value={state.screenFailed ? undefined : publishedScreens}
          route="/digital-screen"
        />
      </div>

      <div className="mt-6 flex items-center justify-between border-t border-[#f0f1f3] pt-4">
        <strong className="text-[12px] font-semibold text-[#444851]">
          {intl.formatMessage({ id: 'pages.home.visualization.recentUpdated' })}
        </strong>
        <span className="text-[10px] text-[#9ca0a8]">
          {intl.formatMessage({
            id:
              state.dashboardFailed || state.screenFailed
                ? 'pages.home.visualization.partialUnavailable'
                : 'pages.home.visualization.sortedByUpdatedAt',
          })}
        </span>
      </div>

      {recentItems.length > 0 ? (
        <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {recentItems.map((item) => (
            <VisualizationCard key={`${item.kind}-${item.id}`} item={item} />
          ))}
        </div>
      ) : loading || allFailed ? (
        <div className="flex min-h-[176px] items-center justify-center text-[11px] text-[#a0a4ac]">
          {intl.formatMessage({
            id: loading
              ? 'pages.home.visualization.loading'
              : 'pages.home.visualization.failed',
          })}
        </div>
      ) : (
        <HomeEmptyState
          icon={LayoutDashboard}
          title={intl.formatMessage({ id: 'pages.home.visualization.empty' })}
          size="medium"
          className="min-h-[176px]"
        />
      )}
    </section>
  );
}
