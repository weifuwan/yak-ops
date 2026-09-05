import type {
  QualityOverviewDimension,
  QualityOverviewView,
} from '@/services/data-quality';
import dayjs, { type Dayjs } from 'dayjs';

import type { DataQualityIntl } from '../i18n';

export type OverviewPeriodKey = 'yesterday' | '7d' | '30d';
export type OverviewSectionKind = 'quality' | 'issue';

export interface OverviewDateRange {
  startDate: string;
  endDate: string;
}

export interface OverviewMetricItem {
  label: string;
  value: string;
  tooltip?: string;
}

const t = (
  intl: DataQualityIntl,
  id: string,
  values?: Record<string, string | number>,
) => intl.formatMessage({ id }, values);

export const resolvePresetRange = (
  period: OverviewPeriodKey,
): OverviewDateRange => {
  const end = dayjs().subtract(1, 'day');
  const days = period === 'yesterday' ? 1 : period === '7d' ? 7 : 30;
  return {
    startDate: end.subtract(days - 1, 'day').format('YYYY-MM-DD'),
    endDate: end.format('YYYY-MM-DD'),
  };
};

export const rangeKey = (range: OverviewDateRange) =>
  `${range.startDate}:${range.endDate}`;

export const toPickerRange = (range: OverviewDateRange): [Dayjs, Dayjs] => [
  dayjs(range.startDate),
  dayjs(range.endDate),
];

export const formatRangeLabel = (range: OverviewDateRange) =>
  `${dayjs(range.startDate).format('MM.DD')}-${dayjs(range.endDate).format('MM.DD')}`;

export const formatPeriodText = (
  range: OverviewDateRange,
  intl: DataQualityIntl,
) =>
  t(intl, 'pages.dataQuality.overview.periodText', {
    start: range.startDate,
    end: range.endDate,
  });

export const formatRate = (value?: number) =>
  value === undefined || value === null ? '--' : `${value.toFixed(1)}%`;

export const formatDuration = (value?: number) => {
  if (value === undefined || value === null) return '--';
  const milliseconds = Math.round(value);
  if (milliseconds < 1000) return `${milliseconds} ms`;
  if (milliseconds < 60_000) return `${(milliseconds / 1000).toFixed(1)} s`;
  return `${(milliseconds / 60_000).toFixed(1)} min`;
};

export const formatCount = (value?: number) =>
  Math.max(0, Number(value || 0)).toLocaleString();

export const findDimension = (
  dimensions: QualityOverviewDimension[] | undefined,
  aliases: readonly string[],
) => dimensions?.find((item) => aliases.includes(item.dimension));

export const buildMetrics = (
  section: OverviewSectionKind,
  tabKey: string,
  overview: QualityOverviewView | undefined,
  intl: DataQualityIntl,
): OverviewMetricItem[] => {
  const summary = overview?.summary;
  if (section === 'issue') {
    if (tabKey === 'dimension') {
      const dimensions = overview?.dimensions ?? [];
      const value = (aliases: string[]) =>
        formatCount(findDimension(dimensions, aliases)?.issues);
      return [
        {
          label: t(
            intl,
            'pages.dataQuality.overview.metric.completenessIssues',
          ),
          value: value(['完整性']),
        },
        {
          label: t(
            intl,
            'pages.dataQuality.overview.metric.uniquenessIssues',
          ),
          value: value(['唯一性']),
        },
        {
          label: t(intl, 'pages.dataQuality.overview.metric.validityIssues'),
          value: value(['有效性']),
        },
        {
          label: t(intl, 'pages.dataQuality.overview.metric.accuracyIssues'),
          value: value(['准确性']),
        },
        {
          label: t(
            intl,
            'pages.dataQuality.overview.metric.timelinessIssues',
          ),
          value: value(['时效性', '及时性']),
        },
        {
          label: t(intl, 'pages.dataQuality.overview.metric.customIssues'),
          value: value(['自定义']),
        },
      ];
    }
    return [
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueExecutions'),
        value: formatCount(summary?.issueExecutionCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.failedRules'),
        value: formatCount(summary?.failedRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.errorRules'),
        value: formatCount(summary?.errorRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.affectedMonitors'),
        value: formatCount(summary?.affectedMonitorCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.affectedTables'),
        value: formatCount(summary?.affectedTableCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.affectedColumns'),
        value: formatCount(summary?.affectedColumnCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueRate'),
        value: formatRate(summary?.issueRate),
        tooltip: t(
          intl,
          'pages.dataQuality.overview.metric.tooltipIssueRate',
        ),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueRules'),
        value: formatCount(summary?.issueRuleCount),
      },
    ];
  }

  if (tabKey === 'monitor') {
    return [
      {
        label: t(intl, 'pages.dataQuality.overview.metric.activeMonitors'),
        value: formatCount(summary?.activeMonitorCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.executionCount'),
        value: formatCount(summary?.executionCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueMonitors'),
        value: formatCount(summary?.affectedMonitorCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueExecutions'),
        value: formatCount(summary?.issueExecutionCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueTables'),
        value: formatCount(summary?.affectedTableCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueFields'),
        value: formatCount(summary?.affectedColumnCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.averageDuration'),
        value: formatDuration(summary?.averageDurationMs),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.latestRun'),
        value: summary?.latestExecutionAt
          ? dayjs(summary.latestExecutionAt).format('MM-DD HH:mm')
          : '--',
      },
    ];
  }

  if (tabKey === 'rule') {
    return [
      {
        label: t(intl, 'pages.dataQuality.overview.metric.executedRules'),
        value: formatCount(summary?.executedRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.passedRules'),
        value: formatCount(summary?.passedRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.failedRules'),
        value: formatCount(summary?.failedRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.errorRules'),
        value: formatCount(summary?.errorRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueRules'),
        value: formatCount(summary?.issueRuleCount),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.passRate'),
        value: formatRate(summary?.passRate),
        tooltip: t(
          intl,
          'pages.dataQuality.overview.metric.tooltipPassRate',
        ),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.issueRate'),
        value: formatRate(summary?.issueRate),
        tooltip: t(
          intl,
          'pages.dataQuality.overview.metric.tooltipIssueRate',
        ),
      },
      {
        label: t(intl, 'pages.dataQuality.overview.metric.activeMonitors'),
        value: formatCount(summary?.activeMonitorCount),
      },
    ];
  }

  return [
    {
      label: t(intl, 'pages.dataQuality.overview.metric.executionCount'),
      value: formatCount(summary?.executionCount),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.activeMonitors'),
      value: formatCount(summary?.activeMonitorCount),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.executedRules'),
      value: formatCount(summary?.executedRuleCount),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.passedRules'),
      value: formatCount(summary?.passedRuleCount),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.failedRules'),
      value: formatCount(summary?.failedRuleCount),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.errorRules'),
      value: formatCount(summary?.errorRuleCount),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.passRate'),
      value: formatRate(summary?.passRate),
      tooltip: t(intl, 'pages.dataQuality.overview.metric.tooltipPassRate'),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.averageDuration'),
      value: formatDuration(summary?.averageDurationMs),
    },
    {
      label: t(intl, 'pages.dataQuality.overview.metric.issueExecutions'),
      value: formatCount(summary?.issueExecutionCount),
    },
  ];
};
