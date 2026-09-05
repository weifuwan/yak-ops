import YakOpsEmpty from '@/components/YakOpsEmpty';
import type { HomeDataCenterOverview } from '@/services/home';
import { useIntl } from '@umijs/max';
import { useMemo } from 'react';

import type { HomeDataCenterPeriodKey } from '../../types';
import { toOverviewMetrics } from '../../utils/homeDataCenter';
import { OverviewMetrics } from './OverviewMetrics';
import { TrendChart } from './TrendChart';

interface OverviewPanelProps {
  overview?: HomeDataCenterOverview;
  periodKey: HomeDataCenterPeriodKey;
  periodLabel: string;
  loading: boolean;
  failed: boolean;
}

export function OverviewPanel({
  overview,
  periodKey,
  periodLabel,
  loading,
  failed,
}: OverviewPanelProps) {
  const intl = useIntl();
  const overviewMetrics = useMemo(
    () =>
      toOverviewMetrics(
        overview,
        periodKey,
        {
          successTasks: intl.formatMessage({
            id: 'pages.home.dataCenter.metric.successTasks',
          }),
          running: intl.formatMessage({
            id: 'pages.home.dataCenter.metric.running',
          }),
          failedTasks: intl.formatMessage({
            id: 'pages.home.dataCenter.metric.failedTasks',
          }),
          scheduleCount: intl.formatMessage({
            id: 'pages.home.dataCenter.metric.scheduleCount',
          }),
          processedRecords: intl.formatMessage({
            id: 'pages.home.dataCenter.metric.processedRecords',
          }),
          avgDuration: intl.formatMessage({
            id: 'pages.home.dataCenter.metric.avgDuration',
          }),
          compare1d: intl.formatMessage({ id: 'pages.home.dataCenter.compare.1d' }),
          compare7d: intl.formatMessage({ id: 'pages.home.dataCenter.compare.7d' }),
          compare30d: intl.formatMessage({ id: 'pages.home.dataCenter.compare.30d' }),
        },
        intl.locale,
      ),
    [intl, overview, periodKey],
  );
  const trendLabels = overview?.trend?.labels || [];
  const trendValues = overview?.trend?.values || [];
  const hasTrendData = trendLabels.length > 0 && trendValues.length > 0;
  const runsLabel = intl.formatMessage({
    id: 'pages.home.dataCenter.overview.runs',
  });

  if (loading || failed) {
    return (
      <div className="flex h-[240px] items-center justify-center text-[12px] text-[#9da1a8]">
        {intl.formatMessage({
          id: loading
            ? 'pages.home.dataCenter.overview.loading'
            : 'pages.home.dataCenter.overview.failed',
        })}
      </div>
    );
  }

  if (!hasTrendData) {
    return (
      <div className="flex h-[240px] items-center justify-center">
        <YakOpsEmpty
          width={120}
          height={80}
          title={intl.formatMessage(
            { id: 'pages.home.dataCenter.overview.empty' },
            { period: periodLabel },
          )}
          showCaption
        />
      </div>
    );
  }

  return (
    <div className="lg:h-[240px]">
      <div className="mt-2 flex items-center justify-end gap-1.5 text-[12px] text-[#7f848e]">
        <span className="h-2 w-2 rounded-full bg-[#5b8cff]" />
        {runsLabel}
      </div>
      <TrendChart
        key={`trend-${periodKey}`}
        values={trendValues}
        labels={trendLabels}
        name={runsLabel}
        height={132}
      />
      <OverviewMetrics metrics={overviewMetrics} />
    </div>
  );
}
