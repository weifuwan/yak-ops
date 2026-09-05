import { YakButton } from '@/components/ui';
import type { DataServiceApi } from '@/services/data-service';
import { useIntl } from '@umijs/max';
import {
  Dropdown,
  Modal,
  Switch,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { ArrowLeft, Copy, MoreHorizontal, Trash2 } from 'lucide-react';
import { useMemo } from 'react';

import { describeDataServiceSource } from '../utils';
import DataServiceMethodBadge from './DataServiceMethodBadge';
import DataServiceSearchBar from './DataServiceSearchBar';

interface DataServiceSearchResultsProps {
  keyword: string;
  submittedKeyword: string;
  loading: boolean;
  records: DataServiceApi[];
  callsByApiId: ReadonlyMap<number, number>;
  canManage: boolean;
  canDelete: boolean;
  dataSourceName: (dataSourceId?: number) => string;
  onKeywordChange: (value: string) => void;
  onSearch: () => void;
  onReset: () => void;
  onOpen: (service: DataServiceApi) => void;
  onCopyEndpoint: (endpoint: string) => void;
  onToggle: (service: DataServiceApi, enabled: boolean) => void;
  onDelete: (service: DataServiceApi) => Promise<void>;
}

const DataServiceSearchResults = ({
  keyword,
  submittedKeyword,
  loading,
  records,
  callsByApiId,
  canManage,
  canDelete,
  dataSourceName,
  onKeywordChange,
  onSearch,
  onReset,
  onOpen,
  onCopyEndpoint,
  onToggle,
  onDelete,
}: DataServiceSearchResultsProps) => {
  const intl = useIntl();

  const confirmDelete = (service: DataServiceApi) => {
    Modal.confirm({
      title: intl.formatMessage({ id: 'pages.dataService.delete.title' }),
      content: intl.formatMessage(
        { id: 'pages.dataService.delete.confirm' },
        { name: service.name },
      ),
      okText: intl.formatMessage({ id: 'pages.dataService.delete.ok' }),
      okButtonProps: { danger: true },
      cancelText: intl.formatMessage({ id: 'pages.dataService.delete.cancel' }),
      onOk: () => onDelete(service),
    });
  };

  const columns = useMemo<TableColumnsType<DataServiceApi>>(
    () => [
      {
        title: 'API',
        dataIndex: 'name',
        minWidth: 230,
        render: (_value, service) => (
          <div className="py-1">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onOpen(service)}
                className="max-w-[220px] truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823]"
              >
                {service.name}
              </button>
              <DataServiceMethodBadge />
            </div>
            <div className="mt-0.5 line-clamp-1 text-[11px] text-[#98a2b3]">
              {service.description ||
                intl.formatMessage({ id: 'pages.dataService.api.noDescription' })}
            </div>
          </div>
        ),
      },
      {
        title: 'Endpoint',
        dataIndex: 'runtimePath',
        minWidth: 280,
        render: (value: string) => (
          <div className="flex items-center gap-1">
            <span className="truncate font-mono text-[11px] text-[#667085]">
              {value}
            </span>
            <Tooltip
              title={intl.formatMessage({
                id: 'pages.dataService.table.copyEndpoint',
              })}
            >
              <YakButton
                type="text"
                size="small"
                iconOnly
                icon={<Copy size={13} />}
                className="!h-6 !w-6 !min-w-0 !p-0"
                onClick={() => onCopyEndpoint(value)}
              />
            </Tooltip>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataService.table.source' }),
        key: 'source',
        width: 210,
        render: (_value, service) => {
          const source = describeDataServiceSource(
            service,
            dataSourceName(service.dataSourceId),
            intl.formatMessage({ id: 'pages.dataService.source.frozen' }),
          );
          return (
            <div>
              <div
                className={
                  source.muted
                    ? 'text-[12px] font-medium text-[#667085]'
                    : 'text-[12px] font-medium text-[#475467]'
                }
              >
                {source.primary}
              </div>
              {source.secondary ? (
                <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                  {source.secondary}
                </div>
              ) : null}
            </div>
          );
        },
      },
      {
        title: intl.formatMessage({ id: 'pages.dataService.table.recentCalls' }),
        key: 'calls',
        width: 100,
        render: (_value, service) => (
          <span className="text-[12px] text-[#475467]">
            {callsByApiId.get(service.id) ?? '—'}
          </span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataService.table.status' }),
        dataIndex: 'enabled',
        width: 135,
        render: (enabled: boolean, service) => (
          <div className="flex items-center gap-2">
            <Switch
              size="small"
              checked={enabled}
              disabled={!canManage}
              onChange={(nextEnabled) => onToggle(service, nextEnabled)}
            />
            <span
              className={
                enabled
                  ? 'text-[12px] text-[#344054]'
                  : 'text-[12px] text-[#98a2b3]'
              }
            >
              {intl.formatMessage({
                id: enabled
                  ? 'pages.dataService.api.running'
                  : 'pages.dataService.api.disabled',
              })}
            </span>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataService.table.actions' }),
        key: 'actions',
        width: 120,
        fixed: 'right',
        render: (_value, service) => (
          <div className="flex items-center gap-2">
            <YakButton
              type="text"
              size="small"
              className="!h-7 !px-0 !text-[12px] !text-[#475467]"
              onClick={() => onOpen(service)}
            >
              {intl.formatMessage({ id: 'pages.dataService.table.view' })}
            </YakButton>
            {canDelete ? (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    {
                      key: 'delete',
                      danger: true,
                      icon: <Trash2 size={14} />,
                      label: intl.formatMessage({
                        id: 'pages.dataService.delete.title',
                      }),
                    },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'delete') confirmDelete(service);
                  },
                }}
              >
                <YakButton
                  type="text"
                  size="small"
                  iconOnly
                  icon={<MoreHorizontal size={16} />}
                  className="!h-7 !w-7 !min-w-0 !p-0"
                />
              </Dropdown>
            ) : null}
          </div>
        ),
      },
    ],
    [
      callsByApiId,
      canDelete,
      canManage,
      dataSourceName,
      intl,
      onCopyEndpoint,
      onDelete,
      onOpen,
      onToggle,
    ],
  );

  return (
    <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
      <header className="flex min-w-0 items-center gap-2">
        <YakButton
          type="text"
          iconOnly
          icon={<ArrowLeft size={15} />}
          className="!-ml-2 !h-8 !w-8 !min-w-0 !p-0"
          onClick={onReset}
        />
        <div className="min-w-0">
          <h1 className="m-0 text-[17px] font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataService.marketplace.title' })}
          </h1>
          <div className="mt-1 truncate text-[12px] text-[#98a2b3]">
            {intl.formatMessage(
              { id: 'pages.dataService.search.keyword' },
              { keyword: submittedKeyword },
            )}
          </div>
        </div>
      </header>

      <div className="mt-4 flex items-center justify-between gap-4">
        <div className="w-full max-w-[680px]">
          <DataServiceSearchBar
            compact
            keyword={keyword}
            loading={loading}
            onKeywordChange={onKeywordChange}
            onSearch={onSearch}
          />
        </div>
        <span className="shrink-0 text-[12px] text-[#98a2b3]">
          {intl.formatMessage(
            { id: 'pages.dataService.search.resultCount' },
            { count: records.length },
          )}
        </span>
      </div>

      <div className="min-h-0 flex-1 pt-4">
        <Table<DataServiceApi>
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={records}
          columns={columns}
          pagination={false}
          scroll={{ x: 1080, y: 'calc(100vh - 235px)' }}
          locale={{
            emptyText: intl.formatMessage({ id: 'pages.dataService.search.empty' }),
          }}
        />
      </div>
    </div>
  );
};

export default DataServiceSearchResults;
