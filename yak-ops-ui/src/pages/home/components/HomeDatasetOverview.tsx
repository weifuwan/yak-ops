import { history, useIntl } from '@umijs/max';
import { Boxes, ChevronRight, Database, Table2 } from 'lucide-react';

import {
  EmptyList,
  formatMetric,
  type HomeAssetOverviewState,
  relativeTime,
  SectionHeader,
} from './homeAssetOverviewShared';
import type { HomeAssetDatasetItem } from './service';

function datasetStatusLabel(status: string | undefined, formatMessage: (id: string) => string) {
  const normalized = status?.toUpperCase();
  if (normalized === 'ONLINE') return formatMessage('pages.home.dataset.status.online');
  if (normalized === 'OFFLINE') return formatMessage('pages.home.dataset.status.offline');
  return status || formatMessage('pages.home.dataset.status.unknown');
}

function AssetOverviewColumn({ state }: { state: HomeAssetOverviewState }) {
  const intl = useIntl();
  const dataset = state.data?.dataset;
  const metrics = [
    {
      label: intl.formatMessage({ id: 'pages.home.dataset.metric.dataset' }),
      value: dataset?.datasetCount,
      icon: <Database size={17} strokeWidth={1.8} />,
    },
    {
      label: intl.formatMessage({ id: 'pages.home.dataset.metric.lineageTable' }),
      value: dataset?.tableAssetCount,
      icon: <Table2 size={17} strokeWidth={1.8} />,
    },
    {
      label: intl.formatMessage({ id: 'pages.home.dataset.metric.lineageColumn' }),
      value: dataset?.columnAssetCount,
      icon: <Boxes size={17} strokeWidth={1.8} />,
    },
  ];

  return (
    <div className="min-w-0 lg:pr-6">
      <div className="flex items-center gap-3 border-b border-[#eef0f3] pb-3">
        <strong className="text-[13px] font-semibold text-[#343842]">
          {intl.formatMessage({ id: 'pages.home.dataset.overview' })}
        </strong>
        <span className="text-[11px] text-[#9da1a9]">
          {intl.formatMessage({ id: 'pages.home.dataset.inLineage' })}
        </span>
      </div>

      <div className="mt-4 space-y-4">
        {metrics.map((item) => (
          <button
            key={item.label}
            type="button"
            onClick={() => history.push('/data-analysis/data-catalog')}
            className="group flex w-full items-center gap-3 border-0 bg-transparent p-0 text-left"
          >
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-[#f2f5ff] text-[#637be7] transition-colors group-hover:bg-[#eaf0ff]">
              {item.icon}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[11px] leading-5 text-[#92969f]">
                {item.label}
              </span>
              <strong className="mt-0.5 block text-[22px] font-semibold leading-7 tracking-[-0.6px] text-[#2f333c]">
                {formatMetric(item.value, intl.locale)}
              </strong>
            </span>
            <ChevronRight
              size={14}
              strokeWidth={1.8}
              className="text-[#c1c4ca] opacity-0 transition-all group-hover:translate-x-0.5 group-hover:opacity-100"
            />
          </button>
        ))}
      </div>

      <div className="mt-5 rounded-[10px] bg-[#f7f8fa] px-3 py-3">
        <div className="flex items-center justify-between">
          <span className="text-[11px] text-[#8e939c]">
            {intl.formatMessage({ id: 'pages.home.dataset.todayCreated' })}
          </span>
          <span className="text-[10px] text-[#a0a4ac]">
            {state.failed
              ? intl.formatMessage({ id: 'pages.home.common.loadFailed' })
              : intl.formatMessage({ id: 'pages.home.dataset.realtimeStats' })}
          </span>
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <strong className="text-[18px] font-semibold text-[#343842]">
            {formatMetric(dataset?.todayCreatedCount, intl.locale)}
          </strong>
          <span className="text-[10px] text-[#a0a4ac]">
            {intl.formatMessage({ id: 'pages.home.dataset.unit' })}
          </span>
        </div>
      </div>
    </div>
  );
}

function DatasetRow({ item }: { item: HomeAssetDatasetItem }) {
  const intl = useIntl();
  return (
    <button
      type="button"
      onClick={() => history.push('/data-analysis/data-catalog')}
      className="group flex w-full items-center gap-3 rounded-[8px] border-0 bg-transparent px-1 py-[11px] text-left transition-colors hover:bg-[#f7f8fa]"
    >
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#f1f4f8] text-[#69717e]">
        <Table2 size={15} strokeWidth={1.8} />
      </span>
      <span className="min-w-0 flex-1">
        <strong className="block truncate text-[12px] font-medium leading-5 text-[#3d414a]">
          {item.name}
        </strong>
        <span className="mt-0.5 block truncate text-[10px] leading-4 text-[#9ca0a8]">
          {datasetStatusLabel(item.status, (id) => intl.formatMessage({ id }))} · #{item.id}
        </span>
      </span>
      <span className="shrink-0 text-[10px] text-[#a0a4ac]">
        {relativeTime(item.updatedAt, intl.locale)}
      </span>
    </button>
  );
}

function RecentDatasetColumn({ state }: { state: HomeAssetOverviewState }) {
  const intl = useIntl();
  const items = state.data?.dataset?.recentDatasets || [];
  return (
    <div className="min-w-0 border-t border-[#eef0f3] py-5 lg:border-l lg:border-t-0 lg:px-6 lg:py-0">
      <div className="flex items-center gap-3 border-b border-[#eef0f3] pb-3">
        <strong className="text-[13px] font-semibold text-[#343842]">
          {intl.formatMessage({ id: 'pages.home.dataset.recentUpdated' })}
        </strong>
        <span className="text-[11px] text-[#9da1a9]">
          {intl.formatMessage({ id: 'pages.home.dataset.byUpdatedAt' })}
        </span>
      </div>

      {items.length > 0 ? (
        <div className="mt-1">
          {items.map((item) => (
            <DatasetRow key={item.id} item={item} />
          ))}
        </div>
      ) : (
        <EmptyList
          loading={state.loading}
          failed={state.failed}
          unavailable={state.data?.dataset?.datasetCount == null}
          text={intl.formatMessage({ id: 'pages.home.dataset.empty' })}
          icon={Table2}
        />
      )}
    </div>
  );
}

function OnlineDatasetColumn({ state }: { state: HomeAssetOverviewState }) {
  const intl = useIntl();
  const items = state.data?.dataset?.onlineDatasets || [];
  return (
    <div className="min-w-0 border-t border-[#eef0f3] pt-5 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
      <div className="flex items-center gap-3 border-b border-[#eef0f3] pb-3">
        <strong className="text-[13px] font-semibold text-[#343842]">
          {intl.formatMessage({ id: 'pages.home.dataset.onlineTitle' })}
        </strong>
        <span className="text-[11px] text-[#9da1a9]">
          {intl.formatMessage({ id: 'pages.home.dataset.byUpdatedAt' })}
        </span>
      </div>

      {items.length > 0 ? (
        <div className="mt-1">
          {items.map((item, index) => {
            const rankClassName =
              index === 0
                ? 'bg-[#ff4d68]'
                : index === 1
                  ? 'bg-[#ff8c31]'
                  : index === 2
                    ? 'bg-[#e9b919]'
                    : 'bg-[#999da5]';
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => history.push('/data-analysis/data-catalog')}
                className="group flex w-full items-center gap-3 rounded-[8px] border-0 bg-transparent px-1 py-[11px] text-left transition-colors hover:bg-[#f7f8fa]"
              >
                <span
                  className={`flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-[4px] text-[10px] font-semibold text-white ${rankClassName}`}
                >
                  {index + 1}
                </span>
                <span className="min-w-0 flex-1 truncate text-[12px] text-[#454952]">
                  {item.name}
                </span>
                <span className="shrink-0 text-[10px] text-[#999da5]">
                  {relativeTime(item.updatedAt, intl.locale)}
                </span>
              </button>
            );
          })}
        </div>
      ) : (
        <EmptyList
          loading={state.loading}
          failed={state.failed}
          unavailable={state.data?.dataset?.datasetCount == null}
          text={intl.formatMessage({ id: 'pages.home.dataset.emptyOnline' })}
          icon={Database}
        />
      )}

      <button
        type="button"
        onClick={() => history.push('/data-analysis/data-catalog')}
        className="mx-auto mt-3 flex items-center gap-0.5 border-0 bg-transparent px-3 py-1 text-[11px] text-[#868b94] transition-colors hover:text-[#343842]"
      >
        {intl.formatMessage({ id: 'pages.home.common.viewAll' })}
        <ChevronRight size={12} strokeWidth={1.8} />
      </button>
    </div>
  );
}

export function DatasetOverview({ state }: { state: HomeAssetOverviewState }) {
  const intl = useIntl();
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-6 pt-5">
      <SectionHeader
        title={intl.formatMessage({ id: 'pages.home.dataset.title' })}
        description=""
        onMore={() => history.push('/data-analysis/data-catalog')}
      />
      <div className="mt-5 grid grid-cols-1 lg:grid-cols-[0.76fr_1.15fr_1fr]">
        <AssetOverviewColumn state={state} />
        <RecentDatasetColumn state={state} />
        <OnlineDatasetColumn state={state} />
      </div>
    </section>
  );
}
