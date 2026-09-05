import YakOpsEmpty from '@/components/YakOpsEmpty';
import type {
  QualityOverviewTrendPoint,
  QualityOverviewView,
} from '@/services/data-quality';
import { useIntl } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { useMemo } from 'react';
import { formatQualityDimension } from '../../i18n';
import type { OverviewSectionKind } from '../utils';
import { formatCount } from '../utils';

interface QualityTrendChartProps {
  overview?: QualityOverviewView;
  section: OverviewSectionKind;
  tabKey: string;
}

interface SeriesDefinition {
  label: string;
  color: string;
  value: (point: QualityOverviewTrendPoint) => number;
}

export default function QualityTrendChart({
  overview,
  section,
  tabKey,
}: QualityTrendChartProps) {
  const intl = useIntl();
  const trend = overview?.trend ?? [];
  const series = useMemo<SeriesDefinition[]>(() => {
    if (section === 'issue') {
      return [
        {
          label: intl.formatMessage({
            id: 'pages.dataQuality.overview.series.failedRules',
          }),
          color: '#fe2c55',
          value: (point) => point.failedRuleCount,
        },
        {
          label: intl.formatMessage({
            id: 'pages.dataQuality.overview.series.errorRules',
          }),
          color: '#f59e0b',
          value: (point) => point.errorRuleCount,
        },
      ];
    }
    if (tabKey === 'monitor') {
      return [
        {
          label: intl.formatMessage({
            id: 'pages.dataQuality.overview.series.activeMonitors',
          }),
          color: '#4f7cff',
          value: (point) => point.activeMonitorCount,
        },
        {
          label: intl.formatMessage({
            id: 'pages.dataQuality.overview.series.issueExecutions',
          }),
          color: '#fe2c55',
          value: (point) => point.issueExecutionCount,
        },
      ];
    }
    if (tabKey === 'rule') {
      return [
        {
          label: intl.formatMessage({
            id: 'pages.dataQuality.overview.series.passedRules',
          }),
          color: '#4f7cff',
          value: (point) => point.passedRuleCount,
        },
        {
          label: intl.formatMessage({
            id: 'pages.dataQuality.overview.series.issueRules',
          }),
          color: '#fe2c55',
          value: (point) => point.failedRuleCount + point.errorRuleCount,
        },
      ];
    }
    return [
      {
        label: intl.formatMessage({
          id: 'pages.dataQuality.overview.series.executionCount',
        }),
        color: '#4f7cff',
        value: (point) => point.executionCount,
      },
      {
        label: intl.formatMessage({
          id: 'pages.dataQuality.overview.series.issueExecutions',
        }),
        color: '#fe2c55',
        value: (point) => point.issueExecutionCount,
      },
    ];
  }, [intl, section, tabKey]);

  if (section === 'issue' && tabKey === 'dimension') {
    const rows = (overview?.dimensions ?? []).filter((item) => item.issues > 0);
    const max = Math.max(1, ...rows.map((item) => item.issues));
    if (!rows.length) {
      return (
        <div className="flex min-h-[320px] items-center justify-center">
          <YakOpsEmpty
            width={180}
            height={124}
            title={intl.formatMessage({
              id: 'pages.dataQuality.overview.empty.dimensionTitle',
            })}
            description={intl.formatMessage({
              id: 'pages.dataQuality.overview.empty.dimensionDesc',
            })}
          />
        </div>
      );
    }
    return (
      <div className="mx-auto w-full max-w-[920px] space-y-4 px-6 py-8">
        {rows.map((item) => (
          <div
            key={item.dimension}
            className="grid grid-cols-[90px_minmax(0,1fr)_70px] items-center gap-3"
          >
            <span className="truncate text-[12px] text-[#667085]">
              {formatQualityDimension(intl, item.dimension)}
            </span>
            <div className="h-3 overflow-hidden rounded-full bg-[#f0f2f5]">
              <div
                className="h-full rounded-full bg-[#4f7cff] transition-[width] duration-300"
                style={{ width: `${Math.max(4, (item.issues / max) * 100)}%` }}
              />
            </div>
            <span className="text-right text-[12px] font-medium text-[#30343b]">
              {formatCount(item.issues)}
            </span>
          </div>
        ))}
      </div>
    );
  }

  const values = trend.flatMap((point) =>
    series.map((item) => item.value(point)),
  );
  const hasData = values.some((value) => value > 0);
  if (!trend.length || !hasData) {
    return (
      <div className="flex min-h-[320px] items-center justify-center">
        <YakOpsEmpty
          width={180}
          height={124}
          title={intl.formatMessage({
            id:
              section === 'issue'
                ? 'pages.dataQuality.overview.empty.issueTrend'
                : 'pages.dataQuality.overview.empty.qualityTrend',
          })}
          description={intl.formatMessage({
            id: 'pages.dataQuality.overview.empty.trendDesc',
          })}
        />
      </div>
    );
  }

  const option: EChartsOption = {
    animationDuration: 260,
    grid: {
      left: 20,
      right: 20,
      top: 48,
      bottom: 20,
      containLabel: true,
    },
    legend: {
      top: 8,
      right: 12,
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: {
        color: '#667085',
        fontSize: 11,
      },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      padding: [10, 12],
      textStyle: {
        color: '#30343b',
        fontSize: 12,
      },
      extraCssText:
        'box-shadow:0 6px 18px rgba(16,24,40,.08);border-radius:8px;',
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: '#d9dde4',
          width: 1,
          type: 'dashed',
        },
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((point) => point.date),
      axisLine: {
        lineStyle: {
          color: '#e7e9ee',
        },
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: '#98a2b3',
        fontSize: 10,
        margin: 12,
        formatter: (value: string) => value.slice(5),
      },
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLine: {
        show: false,
      },
      axisTick: {
        show: false,
      },
      axisLabel: {
        color: '#a0a6af',
        fontSize: 10,
        margin: 12,
      },
      splitLine: {
        lineStyle: {
          color: '#eef0f3',
          width: 1,
        },
      },
    },
    series: series.map((item) => ({
      name: item.label,
      type: 'line',
      data: trend.map((point) => item.value(point)),
      smooth: false,
      showSymbol: true,
      symbol: 'circle',
      symbolSize: trend.length === 1 ? 8 : 6,
      lineStyle: {
        color: item.color,
        width: 2,
      },
      itemStyle: {
        color: '#fff',
        borderColor: item.color,
        borderWidth: 2,
      },
      emphasis: {
        focus: 'series',
        itemStyle: {
          color: '#fff',
          borderColor: item.color,
          borderWidth: 2,
        },
      },
    })),
  };

  return (
    <div className="min-h-[320px] px-3 pb-2 pt-2">
      <ReactECharts
        option={option}
        notMerge
        lazyUpdate
        style={{ width: '100%', height: 310 }}
        opts={{ renderer: 'svg' }}
      />
    </div>
  );
}
