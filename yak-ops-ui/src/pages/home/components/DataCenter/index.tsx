import YakTab from '@/components/YakTab';
import { useIntl } from '@umijs/max';
import { ChevronRight, CircleHelp } from 'lucide-react';
import { useMemo } from 'react';

import { useHomeDataCenter } from '../../hooks/useHomeDataCenter';
import type { HomeDataCenterTabKey } from '../../types';
import {
  buildPeriod,
  formatDate,
  formatIsoDate,
} from '../../utils/homeDataCenter';
import { OVERVIEW_TABS, PERIOD_OPTIONS } from './constants';
import { LatestTaskCard } from './LatestTaskCard';
import { OverviewPanel } from './OverviewPanel';
import { PeriodSelect } from './PeriodSelect';
import { RecentTasksPanel } from './RecentTasksPanel';
import { SchedulePanel } from './SchedulePanel';

export default function DataCenter() {
  const intl = useIntl();
  const {
    activeTab,
    setActiveTab,
    periodKey,
    setPeriodKey,
    overview,
    recentTasks,
    scheduleItems,
    overviewLoading,
    overviewFailed,
    recentLoading,
    recentFailed,
    scheduleLoading,
    scheduleFailed,
  } = useHomeDataCenter();

  const fallbackPeriod = useMemo(() => buildPeriod(periodKey), [periodKey]);
  const periodOption = PERIOD_OPTIONS.find((item) => item.key === periodKey)!;
  const periodLabel = intl.formatMessage({ id: periodOption.messageId });
  const periodStart = overview?.period?.start
    ? formatIsoDate(overview.period.start)
    : formatDate(fallbackPeriod.start);
  const periodEnd = overview?.period?.end
    ? formatIsoDate(overview.period.end)
    : formatDate(fallbackPeriod.end);

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <h2 className="shrink-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
            {intl.formatMessage({ id: 'pages.home.dataCenter.title' })}
          </h2>
          <CircleHelp
            size={14}
            strokeWidth={1.9}
            className="shrink-0 text-[#a0a4ac]"
          />
          <span className="ml-0.5 hidden text-[12px] leading-5 text-[#8d929b] sm:inline">
            {intl.formatMessage(
              { id: 'pages.home.dataCenter.periodSummary' },
              { start: periodStart, end: periodEnd },
            )}
          </span>
        </div>

        <button
          type="button"
          className="flex items-center gap-0.5 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
        >
          {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      </header>

      <div className="mt-4 flex flex-col gap-5 lg:flex-row lg:gap-6">
        <LatestTaskCard
          task={overview?.latestTask}
          loading={overviewLoading}
          failed={overviewFailed}
        />

        <div className="min-w-0 flex-1">
          <YakTab
            activeKey={activeTab}
            onChange={(key) => setActiveTab(key as HomeDataCenterTabKey)}
            items={OVERVIEW_TABS.map((item) => ({
              key: item.key,
              label: intl.formatMessage({ id: item.messageId }),
            }))}
            tabBarExtraContent={
              activeTab !== 'recent' ? (
                <div className="mb-1.5">
                  <PeriodSelect value={periodKey} onChange={setPeriodKey} />
                </div>
              ) : undefined
            }
          />

          {activeTab === 'overview' && (
            <OverviewPanel
              overview={overview}
              periodKey={periodKey}
              periodLabel={periodLabel}
              loading={overviewLoading}
              failed={overviewFailed}
            />
          )}

          {activeTab === 'recent' && (
            <RecentTasksPanel
              items={recentTasks}
              loading={recentLoading}
              failed={recentFailed}
            />
          )}

          {activeTab === 'schedule' && (
            <SchedulePanel
              items={scheduleItems}
              periodLabel={periodLabel}
              loading={scheduleLoading}
              failed={scheduleFailed}
            />
          )}
        </div>
      </div>
    </section>
  );
}
