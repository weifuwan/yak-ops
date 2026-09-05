import { formatQualityDimension } from '@/pages/data-quality/i18n';
import {
  homeQualityOverviewApi,
  type HomeQualityDimension,
  type HomeQualityIssue,
  type HomeQualityOverview,
} from '@/services/home';
import { BRAND_COLOR } from '@/styles/brand';
import { history, useIntl } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
  ShieldCheck,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';
import { relativeTime, SectionHeader } from './homeAssetOverviewShared';

interface QualitySidebarState {
  data?: HomeQualityOverview;
  loading: boolean;
  failed: boolean;
}

type RadarDimensionKey =
  | 'completeness'
  | 'uniqueness'
  | 'validity'
  | 'accuracy'
  | 'timeliness';

interface RadarDimensionDefinition {
  key: RadarDimensionKey;
  aliases: string[];
}

const RADAR_DIMENSIONS: RadarDimensionDefinition[] = [
  { key: 'completeness', aliases: ['完整性'] },
  { key: 'uniqueness', aliases: ['唯一性'] },
  { key: 'validity', aliases: ['有效性'] },
  { key: 'accuracy', aliases: ['准确性'] },
  { key: 'timeliness', aliases: ['时效性', '及时性'] },
];

const formatMetric = (value: number | null | undefined, locale: string) =>
  value == null ? '--' : new Intl.NumberFormat(locale).format(value);

const formatRate = (value?: number | null) =>
  value == null ? '--' : value.toFixed(1);

const normalizeRadarDimensions = (
  dimensions: HomeQualityDimension[],
  resolveLabel: (key: RadarDimensionKey) => string,
): HomeQualityDimension[] =>
  RADAR_DIMENSIONS.map((definition) => {
    const matched = dimensions.find((item) =>
      definition.aliases.includes(item.dimension),
    );

    return {
      dimension: resolveLabel(definition.key),
      total: matched?.total ?? 0,
      issues: matched?.issues ?? 0,
      passRate: matched?.passRate ?? null,
    };
  });

const healthState = (
  passRate: number | null | undefined,
  labels: {
    noData: string;
    healthy: string;
    attention: string;
    risky: string;
  },
) => {
  if (passRate == null) {
    return {
      label: labels.noData,
      className: 'text-[#858b94]',
      icon: null,
    };
  }

  if (passRate >= 95) {
    return {
      label: labels.healthy,
      className: 'text-[#31865a]',
      icon: <CheckCircle2 size={13} strokeWidth={2} />,
    };
  }

  return {
    label: passRate >= 80 ? labels.attention : labels.risky,
    className: passRate >= 80 ? 'text-[#b87520]' : 'text-[#d94d59]',
    icon: <AlertTriangle size={13} strokeWidth={1.9} />,
  };
};

function useQualitySidebarOverview(): QualitySidebarState {
  const [state, setState] = useState<QualitySidebarState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;

    homeQualityOverviewApi
      .overview()
      .then((response) => {
        if (!active) return;
        if (!response.data) {
          setState({ loading: false, failed: true });
          return;
        }
        setState({ data: response.data, loading: false, failed: false });
      })
      .catch(() => {
        if (active) setState({ loading: false, failed: true });
      });

    return () => {
      active = false;
    };
  }, []);

  return state;
}

function buildRadarOption(dimensions: HomeQualityDimension[]): EChartsOption {
  const hasCompleteRadar = dimensions.every((item) => item.passRate != null);

  return {
    animation: hasCompleteRadar,
    animationDuration: 520,
    tooltip: hasCompleteRadar
      ? {
          trigger: 'item',
          formatter: () =>
            dimensions
              .map((item) => `${item.dimension}: ${formatRate(item.passRate)}%`)
              .join('<br/>'),
        }
      : { show: false },
    radar: {
      center: ['50%', '51%'],
      radius: '62%',
      splitNumber: 4,
      indicator: dimensions.map((item) => ({
        name: item.dimension,
        max: 100,
      })),
      axisName: {
        color: '#666d78',
        fontSize: 10,
        fontWeight: 500,
      },
      axisLine: { lineStyle: { color: '#dde1e7' } },
      splitLine: { lineStyle: { color: '#e2e5ea' } },
      splitArea: { areaStyle: { color: ['#ffffff', '#f8f9fb'] } },
    },
    series: hasCompleteRadar
      ? [
          {
            type: 'radar',
            symbol: 'circle',
            symbolSize: 4,
            data: [
              {
                value: dimensions.map((item) => item.passRate ?? 0),
                lineStyle: { width: 2, color: BRAND_COLOR },
                itemStyle: { color: BRAND_COLOR },
                areaStyle: { color: 'rgba(254,44,85,0.09)' },
              },
            ],
          },
        ]
      : [],
  };
}

function QualityMetric({
  label,
  value,
  warning = false,
}: {
  label: string;
  value?: number | null;
  warning?: boolean;
}) {
  const intl = useIntl();
  return (
    <div className="min-w-0 text-center">
      <div className="truncate text-[10px] leading-4 text-[#92969f]">
        {label}
      </div>
      <strong
        className={`mt-1 block text-[17px] font-semibold leading-6 ${
          warning && (value ?? 0) > 0 ? 'text-[#d94d59]' : 'text-[#343943]'
        }`}
      >
        {formatMetric(value, intl.locale)}
      </strong>
    </div>
  );
}

const objectLabel = (issue: HomeQualityIssue) =>
  issue.objectName || issue.tableName || issue.monitorName;

function RecentIssueRow({ issue }: { issue: HomeQualityIssue }) {
  const intl = useIntl();
  return (
    <button
      type="button"
      onClick={() =>
        history.push(
          `/data-quality/execution/${encodeURIComponent(issue.executionNo)}`,
        )
      }
      className="group flex w-full items-center gap-2.5 border-0 bg-transparent py-2.5 text-left"
    >
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[8px] bg-[#fff2f3] text-[#e35d69]">
        <AlertTriangle size={13} strokeWidth={1.9} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-1.5">
          <strong className="truncate text-[11px] font-medium text-[#42464f]">
            {issue.ruleName}
          </strong>
          <span className="shrink-0 rounded-full bg-[#f0f1f4] px-1.5 py-0.5 text-[9px] text-[#747a84]">
            {formatQualityDimension(intl, issue.dimension)}
          </span>
        </span>
        <span className="mt-0.5 block truncate text-[9px] text-[#999ea7]">
          {objectLabel(issue)}
          {issue.columnName ? ` · ${issue.columnName}` : ''}
        </span>
      </span>
      <span className="shrink-0 text-[9px] text-[#a0a4ac]">
        {relativeTime(issue.queuedAt, intl.locale)}
      </span>
      <ChevronRight
        size={12}
        strokeWidth={1.8}
        className="shrink-0 text-[#b0b4bb] transition-transform group-hover:translate-x-0.5"
      />
    </button>
  );
}

export default function HomeQualitySidebarOverview() {
  const intl = useIntl();
  const state = useQualitySidebarOverview();
  const data = state.data;
  const health = healthState(data?.passRate, {
    noData: intl.formatMessage({ id: 'pages.home.quality.health.noData' }),
    healthy: intl.formatMessage({ id: 'pages.home.quality.health.healthy' }),
    attention: intl.formatMessage({ id: 'pages.home.quality.health.attention' }),
    risky: intl.formatMessage({ id: 'pages.home.quality.health.risky' }),
  });
  const dimensions = useMemo(
    () =>
      normalizeRadarDimensions(data?.dimensions ?? [], (key) =>
        intl.formatMessage({ id: `pages.home.quality.dimension.${key}` }),
      ),
    [data?.dimensions, intl.locale],
  );
  const radarOption = useMemo(() => buildRadarOption(dimensions), [dimensions]);
  const issues = data?.recentIssues?.slice(0, 3) ?? [];
  const showRadar = !state.loading && !state.failed && data?.passRate != null;

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-5 pt-5">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.quality.title' })}
        description=""
        onMore={() => history.push('/data-quality/overview')}
      />

      <div className="mt-4 rounded-[14px] border border-[#eef0f3] bg-[#fafbfc] px-4 pb-3.5 pt-3.5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="text-[10px] text-[#92969f]">
              {intl.formatMessage({ id: 'pages.home.quality.overallPassRate' })}
            </span>
            <div className="mt-0.5 flex items-end gap-1">
              <strong className="text-[28px] font-semibold leading-8 tracking-[-0.7px] text-[#2d313a]">
                {formatRate(data?.passRate)}
              </strong>
              {data?.passRate != null ? (
                <span className="mb-0.5 text-[10px] text-[#7e848e]">%</span>
              ) : null}
            </div>
          </div>

          <span
            className={`mt-1 flex items-center gap-1 text-[10px] font-medium ${health.className}`}
          >
            {health.icon}
            {state.loading
              ? intl.formatMessage({ id: 'pages.home.dataCenter.latest.loading' })
              : state.failed
                ? intl.formatMessage({ id: 'pages.home.common.loadFailed' })
                : health.label}
          </span>
        </div>

        <div className="mt-1 h-[176px]">
          {showRadar ? (
            <ReactECharts
              option={radarOption}
              notMerge
              style={{ width: '100%', height: '176px' }}
            />
          ) : state.loading || state.failed ? (
            <div className="flex h-full items-center justify-center text-[10px] text-[#9da1a8]">
              {intl.formatMessage({
                id: state.loading
                  ? 'pages.home.quality.loading'
                  : 'pages.home.quality.failed',
              })}
            </div>
          ) : (
            <HomeEmptyState
              icon={ShieldCheck}
              title={intl.formatMessage({ id: 'pages.home.quality.health.noData' })}
              size="medium"
              className="h-full"
            />
          )}
        </div>

        <div className="grid grid-cols-4 gap-2 border-t border-[#e6e9ee] pt-3">
          <QualityMetric
            label={intl.formatMessage({ id: 'pages.home.quality.metric.monitoredTables' })}
            value={data?.monitoredTableCount}
          />
          <QualityMetric
            label={intl.formatMessage({ id: 'pages.home.quality.metric.todayChecks' })}
            value={data?.todayExecutionCount}
          />
          <QualityMetric
            label={intl.formatMessage({ id: 'pages.home.quality.metric.issueTables' })}
            value={data?.todayIssueTableCount}
            warning
          />
          <QualityMetric
            label={intl.formatMessage({ id: 'pages.home.quality.metric.enabledRules' })}
            value={data?.enabledRuleCount}
          />
        </div>
      </div>

      <div className="mt-4 border-t border-[#eef0f3] pt-3.5">
        <div className="flex items-center justify-between gap-3">
          <strong className="text-[12px] font-semibold text-[#454a53]">
            {intl.formatMessage({ id: 'pages.home.quality.recentIssues' })}
          </strong>
          <span className="flex items-center gap-1 text-[10px] text-[#8f949d]">
            <AlertTriangle size={11} strokeWidth={1.8} className="text-[#e35d69]" />
            {intl.formatMessage(
              { id: 'pages.home.quality.issueCount' },
              { count: formatMetric(data?.recentIssueCount, intl.locale) },
            )}
          </span>
        </div>

        {issues.length > 0 ? (
          <div className="mt-1 divide-y divide-[#f0f1f3]">
            {issues.map((issue) => (
              <RecentIssueRow key={issue.id} issue={issue} />
            ))}
          </div>
        ) : state.loading || state.failed || data?.recentIssueCount == null ? (
          <div className="flex min-h-[92px] items-center justify-center text-[10px] text-[#9da1a8]">
            {intl.formatMessage({
              id: state.loading
                ? 'pages.home.quality.issueLoading'
                : state.failed
                  ? 'pages.home.quality.issueFailed'
                  : 'pages.home.quality.issueUnavailable',
            })}
          </div>
        ) : (
          <HomeEmptyState
            icon={CheckCircle2}
            title={intl.formatMessage({ id: 'pages.home.quality.emptyIssues' })}
            size="small"
            className="min-h-[92px]"
          />
        )}
      </div>
    </section>
  );
}
