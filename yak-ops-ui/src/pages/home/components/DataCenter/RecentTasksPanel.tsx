import type { HomeRecentTask } from '@/services/home';
import { history, useIntl } from '@umijs/max';
import { Clock3, Database } from 'lucide-react';

import {
  formatDuration,
  formatRunTime,
  statusClassName,
  statusLabel,
  type HomeStatusKey,
} from '../../utils/homeDataCenter';
import { HomeEmptyState } from '../HomeEmptyState';

interface RecentTasksPanelProps {
  items: HomeRecentTask[];
  loading: boolean;
  failed: boolean;
}

export function RecentTasksPanel({
  items,
  loading,
  failed,
}: RecentTasksPanelProps) {
  const intl = useIntl();
  const resolveStatus = (key: HomeStatusKey) =>
    intl.formatMessage({ id: `pages.home.dataCenter.status.${key}` });
  const todayLabel = intl.formatMessage({ id: 'pages.home.dataCenter.today' });

  if (loading || failed) {
    return (
      <div className="flex min-h-[263px] items-center justify-center text-[12px] text-[#9da1a8]">
        {intl.formatMessage({
          id: loading
            ? 'pages.home.dataCenter.recent.loading'
            : 'pages.home.dataCenter.recent.failed',
        })}
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <HomeEmptyState
        icon={Database}
        title={intl.formatMessage({ id: 'pages.home.dataCenter.recent.empty' })}
        size="medium"
        className="min-h-[263px]"
      />
    );
  }

  const metricLabels = [
    intl.formatMessage({ id: 'pages.home.dataCenter.recent.run' }),
    intl.formatMessage({ id: 'pages.home.dataCenter.recent.success' }),
    intl.formatMessage({ id: 'pages.home.dataCenter.recent.failedMetric' }),
    intl.formatMessage({ id: 'pages.home.dataCenter.recent.duration' }),
  ];

  return (
    <div className="min-h-[263px] pt-2">
      {items.map((item) => (
        <button
          key={`${item.taskType}-${item.taskId}`}
          type="button"
          onClick={() => {
            if (item.detailPath) history.push(item.detailPath);
          }}
          className="group grid w-full grid-cols-[minmax(230px,1.5fr)_repeat(4,minmax(72px,.55fr))_150px] items-center gap-3 rounded-[8px] px-3 py-3 text-left transition-colors hover:bg-[#f7f8fa]"
        >
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#edf4ff] text-[#5b8cff]">
              <Database size={16} strokeWidth={1.9} />
            </div>
            <div className="min-w-0">
              <div className="truncate text-[12px] font-medium text-[#363a43]">
                {item.taskName}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[11px] text-[#969aa3]">
                <Clock3 size={11} strokeWidth={1.8} />
                {formatRunTime(item.lastRunTime, {
                  locale: intl.locale,
                  todayLabel,
                })}
              </div>
            </div>
          </div>

          {[
            [metricLabels[0], String(item.runCount)],
            [metricLabels[1], String(item.successCount)],
            [metricLabels[2], String(item.failedCount)],
            [metricLabels[3], formatDuration(item.lastDurationMs)],
          ].map(([label, value]) => (
            <div key={label} className="min-w-0">
              <span className="text-[11px] text-[#969aa3]">{label}</span>
              <strong className="ml-2 text-[12px] font-semibold text-[#3b3f48]">
                {value}
              </strong>
            </div>
          ))}

          <div className="flex items-center justify-end gap-4">
            <span
              className={`text-[11px] font-medium ${statusClassName(item.lastStatus)}`}
            >
              {statusLabel(item.lastStatus, resolveStatus)}
            </span>
            <span className="text-[12px] font-semibold text-[#323640] transition-colors group-hover:text-[#5b8cff]">
              {intl.formatMessage({ id: 'pages.home.dataCenter.recent.detail' })}
            </span>
          </div>
        </button>
      ))}
    </div>
  );
}
