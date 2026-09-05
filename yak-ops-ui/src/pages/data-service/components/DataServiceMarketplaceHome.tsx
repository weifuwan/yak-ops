import type { DataServiceApi } from '@/services/data-service';
import { useIntl } from '@umijs/max';
import { Empty } from 'antd';

import DataServiceApiListItem from './DataServiceApiListItem';
import DataServiceMarketplaceIllustration from './DataServiceMarketplaceIllustration';
import DataServiceSearchBar from './DataServiceSearchBar';

interface DataServiceMarketplaceHomeProps {
  keyword: string;
  loading: boolean;
  recommendedServices: DataServiceApi[];
  hotServices: DataServiceApi[];
  callsByApiId: ReadonlyMap<number, number>;
  totalServices: number;
  runningServices: number;
  totalCalls?: number;
  canObserve: boolean;
  dataSourceName: (dataSourceId?: number) => string;
  onKeywordChange: (value: string) => void;
  onSearch: () => void;
  onOpen: (service: DataServiceApi) => void;
}

const DataServiceMarketplaceHome = ({
  keyword,
  loading,
  recommendedServices,
  hotServices,
  callsByApiId,
  totalServices,
  runningServices,
  totalCalls,
  canObserve,
  dataSourceName,
  onKeywordChange,
  onSearch,
  onOpen,
}: DataServiceMarketplaceHomeProps) => {
  const intl = useIntl();

  return (
    <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white">
      <section
        className="relative overflow-hidden px-5 py-9"
        style={{
          background:
            'linear-gradient(180deg, #f7f9fc 0%, #fbfcfd 66%, #ffffff 100%)',
        }}
      >
        <div className="pointer-events-none absolute left-[4.5%] top-1/2 hidden -translate-y-1/2 opacity-90 lg:block">
          <DataServiceMarketplaceIllustration />
        </div>
        <div className="pointer-events-none absolute -left-14 bottom-[-92px] h-[190px] w-[190px] rounded-full bg-[rgba(254,44,85,.025)]" />
        <div className="pointer-events-none absolute left-[18%] top-[-90px] h-[160px] w-[160px] rounded-full border border-[rgba(254,44,85,.04)]" />

        <div className="relative mx-auto flex max-w-[820px] flex-col items-center">
          <h1 className="m-0 mb-4 text-[20px] font-semibold tracking-[-.01em] text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataService.marketplace.title' })}
          </h1>
          <DataServiceSearchBar
            keyword={keyword}
            loading={loading}
            onKeywordChange={onKeywordChange}
            onSearch={onSearch}
          />
        </div>
      </section>

      <div className="mx-auto w-full max-w-[1480px] px-5 pb-10 pt-7">
        <div className="grid grid-cols-1 gap-7 xl:grid-cols-[minmax(0,1fr)_340px]">
          <section className="min-w-0">
            <div className="mb-3 flex items-end justify-between px-1">
              <div>
                <h2 className="m-0 text-[15px] font-semibold text-[#161823]">
                  {intl.formatMessage({ id: 'pages.dataService.marketplace.recommended' })}
                </h2>
                <div className="mt-1 text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({
                    id: 'pages.dataService.marketplace.recommendedDescription',
                  })}
                </div>
              </div>
              <span className="text-[11px] text-[#98a2b3]">
                {intl.formatMessage(
                  { id: 'pages.dataService.marketplace.count' },
                  { count: recommendedServices.length },
                )}
              </span>
            </div>

            {recommendedServices.length > 0 ? (
              <div className="space-y-2">
                {recommendedServices.map((service) => (
                  <DataServiceApiListItem
                    key={service.id}
                    service={service}
                    dataSourceName={dataSourceName(service.dataSourceId)}
                    onOpen={() => onOpen(service)}
                  />
                ))}
              </div>
            ) : (
              <div className="flex h-[300px] items-center justify-center rounded-xl bg-[#fafbfc]">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={intl.formatMessage({
                    id: 'pages.dataService.marketplace.noOnlineApi',
                  })}
                />
              </div>
            )}
          </section>

          <aside className="space-y-4">
            <section className="rounded-xl bg-[#f7f8fa] p-4">
              <div className="text-[13px] font-semibold text-[#30323b]">
                {intl.formatMessage({ id: 'pages.dataService.marketplace.overview' })}
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2">
                <div className="rounded-lg bg-white px-3 py-3">
                  <div className="text-[10px] text-[#98a2b3]">
                    {intl.formatMessage({ id: 'pages.dataService.marketplace.totalApis' })}
                  </div>
                  <div className="mt-1 text-[21px] font-semibold tabular-nums text-[#161823]">
                    {totalServices}
                  </div>
                </div>
                <div className="rounded-lg bg-white px-3 py-3">
                  <div className="text-[10px] text-[#98a2b3]">
                    {intl.formatMessage({ id: 'pages.dataService.marketplace.running' })}
                  </div>
                  <div className="mt-1 text-[21px] font-semibold tabular-nums text-[#161823]">
                    {runningServices}
                  </div>
                </div>
                <div className="rounded-lg bg-white px-3 py-3">
                  <div className="text-[10px] text-[#98a2b3]">
                    {intl.formatMessage({ id: 'pages.dataService.marketplace.recentCalls' })}
                  </div>
                  <div className="mt-1 text-[21px] font-semibold tabular-nums text-[#161823]">
                    {canObserve ? totalCalls || 0 : '—'}
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-xl bg-[#f7f8fa] p-4">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <div className="text-[13px] font-semibold text-[#30323b]">
                    {intl.formatMessage({ id: 'pages.dataService.marketplace.hotCalls' })}
                  </div>
                  <div className="mt-1 text-[10px] text-[#98a2b3]">
                    {intl.formatMessage({
                      id: 'pages.dataService.marketplace.hotCallsDescription',
                    })}
                  </div>
                </div>
                <span className="text-[10px] text-[#98a2b3]">
                  Top {hotServices.length}
                </span>
              </div>

              {hotServices.length > 0 ? (
                <div className="space-y-2">
                  {hotServices.map((service, index) => (
                    <DataServiceApiListItem
                      key={service.id}
                      rank={index + 1}
                      service={service}
                      dataSourceName={dataSourceName(service.dataSourceId)}
                      calls={callsByApiId.get(service.id) || 0}
                      onOpen={() => onOpen(service)}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex h-[190px] items-center justify-center rounded-lg bg-white">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={intl.formatMessage({
                      id: canObserve
                        ? 'pages.dataService.marketplace.noCallRecords'
                        : 'pages.dataService.marketplace.noObservePermission',
                    })}
                  />
                </div>
              )}
            </section>
          </aside>
        </div>
      </div>
    </div>
  );
};

export default DataServiceMarketplaceHome;
