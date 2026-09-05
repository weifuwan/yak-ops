import {
  getDataServiceOverview,
  type DataServiceOverview,
} from '@/services/data-service';
import { history, useIntl } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import { Activity } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';
import { SectionHeader } from './homeAssetOverviewShared';

interface DataServiceOverviewState {
  data?: DataServiceOverview;
  loading: boolean;
  failed: boolean;
}

const formatMetric = (value: number | null | undefined, locale: string) =>
  value == null ? '--' : new Intl.NumberFormat(locale).format(value);

const formatRate = (data?: DataServiceOverview) =>
  !data || data.totalCalls <= 0 ? '--' : `${data.successRate.toFixed(1)}%`;

function useDataServiceOverview(): DataServiceOverviewState {
  const [state, setState] = useState<DataServiceOverviewState>({
    loading: true,
    failed: false,
  });

  useEffect(() => {
    let active = true;
    getDataServiceOverview('7d')
      .then((data) => {
        if (!active) return;
        if (!data) {
          setState({ loading: false, failed: true });
          return;
        }
        setState({ data, loading: false, failed: false });
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

function buildTrendOption(
  data: DataServiceOverview | undefined,
  seriesName: string,
): EChartsOption {
  const trend = data?.trend || [];
  return {
    animation: true,
    animationDuration: 520,
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#fff',
      borderColor: '#e8ebef',
      textStyle: { color: '#4b5059', fontSize: 10 },
    },
    grid: { top: 10, left: 8, right: 8, bottom: 22, containLabel: false },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((item) => item.time),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        interval: 3,
        color: '#a0a4ac',
        fontSize: 9,
        formatter: (value: string) => value.slice(0, 5),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      splitLine: { lineStyle: { color: '#f1f3f6' } },
    },
    series: [
      {
        name: seriesName,
        type: 'line',
        smooth: 0.38,
        symbol: 'none',
        data: trend.map((item) => item.calls),
        lineStyle: { width: 2, color: '#6490ee' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(100,144,238,0.18)' },
              { offset: 1, color: 'rgba(100,144,238,0.01)' },
            ],
          },
        },
      },
    ],
  };
}

function OverviewMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 px-4 first:pl-0 last:pr-0">
      <div className="truncate text-[11px] text-[#92969f]">{label}</div>
      <strong className="mt-1 block truncate text-[24px] font-semibold tracking-[-0.6px] text-[#30343d]">
        {value}
      </strong>
    </div>
  );
}

function TrendEmpty({ state }: { state: DataServiceOverviewState }) {
  const intl = useIntl();
  if (state.loading || state.failed) {
    return (
      <div className="flex h-[126px] items-center justify-center text-[11px] text-[#a0a4ac]">
        {intl.formatMessage({
          id: state.loading
            ? 'pages.home.dataService.loading'
            : 'pages.home.dataService.failed',
        })}
      </div>
    );
  }

  return (
    <HomeEmptyState
      icon={Activity}
      title={intl.formatMessage({ id: 'pages.home.dataService.empty' })}
      size="small"
      className="h-[126px]"
    />
  );
}

export default function HomeDataServiceOverview() {
  const intl = useIntl();
  const state = useDataServiceOverview();
  const data = state.data;
  const seriesName = intl.formatMessage({ id: 'pages.home.dataService.series.calls' });
  const trendOption = useMemo(
    () => buildTrendOption(data, seriesName),
    [data, seriesName],
  );
  const hasCalls = (data?.totalCalls || 0) > 0;
  const topApi = data?.hotApis?.[0];

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.dataService.title' })}
        description=""
        onMore={() => history.push('/data-service/overview')}
      />

      <div className="mt-5">
        <div className="grid grid-cols-2 divide-x divide-[#eef0f3] lg:grid-cols-4">
          <OverviewMetric
            label={intl.formatMessage({ id: 'pages.home.dataService.metric.apiTotal' })}
            value={formatMetric(data?.apiTotal, intl.locale)}
          />
          <OverviewMetric
            label={intl.formatMessage({ id: 'pages.home.dataService.metric.running' })}
            value={formatMetric(data?.runningApis, intl.locale)}
          />
          <OverviewMetric
            label={intl.formatMessage({ id: 'pages.home.dataService.metric.calls7d' })}
            value={formatMetric(data?.totalCalls, intl.locale)}
          />
          <OverviewMetric
            label={intl.formatMessage({ id: 'pages.home.dataService.metric.successRate' })}
            value={formatRate(data)}
          />
        </div>

        <div className="mt-5 flex items-center justify-between gap-3 text-[10px] text-[#9da1a9]">
          <span>{intl.formatMessage({ id: 'pages.home.dataService.trend' })}</span>
          {data ? (
            <span className="truncate text-right">
              {intl.formatMessage(
                { id: 'pages.home.dataService.summary' },
                {
                  duration: formatMetric(data.averageDurationMs, intl.locale),
                  failures: formatMetric(data.failureCalls, intl.locale),
                },
              )}
            </span>
          ) : (
            <span>
              {state.failed
                ? intl.formatMessage({ id: 'pages.home.dataService.unavailable' })
                : '--'}
            </span>
          )}
        </div>

        <div className="mt-1 h-[126px]">
          {hasCalls ? (
            <ReactECharts
              option={trendOption}
              style={{ width: '100%', height: '126px' }}
              notMerge
              lazyUpdate
            />
          ) : (
            <TrendEmpty state={state} />
          )}
        </div>

        <div className="mt-2 flex min-h-[30px] items-center justify-between gap-3 border-t border-[#f0f1f3] pt-3 text-[10px]">
          <span className="shrink-0 text-[#9da1a9]">
            {intl.formatMessage({ id: 'pages.home.dataService.mostCalled' })}
          </span>
          {topApi ? (
            <button
              type="button"
              onClick={() => history.push('/data-service/overview')}
              className="min-w-0 border-0 bg-transparent p-0 text-right text-[#646a74] transition-colors hover:text-[#343842]"
            >
              <span className="block truncate">
                {topApi.name || topApi.path || `API #${topApi.apiId}`}
                <strong className="ml-1 font-semibold text-[#454a54]">
                  {intl.formatMessage(
                    { id: 'pages.home.dataService.calls' },
                    { count: formatMetric(topApi.calls, intl.locale) },
                  )}
                </strong>
              </span>
            </button>
          ) : (
            <span className="truncate text-right text-[#a0a4ac]">
              {intl.formatMessage({
                id: state.loading
                  ? 'pages.home.dataCenter.latest.loading'
                  : state.failed
                    ? 'pages.home.dataService.unavailable'
                    : 'pages.home.dataService.noCalls',
              })}
            </span>
          )}
        </div>
      </div>
    </section>
  );
}
