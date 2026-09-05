import { DoubleRightOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Empty, Popover } from 'antd';
import type { ReactNode } from 'react';

import DatabaseIcons from '../../../../data-source/icon/DatabaseIcons';

interface DataSourceSyncPlanProps {
  record: any;
}

const safeParse = (value: any) => {
  if (!value) return null;
  if (typeof value === 'object') return value;
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }
  return null;
};

const getTableList = (tableValue: any): string[] => {
  if (!tableValue) return [];
  let value = tableValue;

  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) return [];
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
      return [trimmed];
    }
    const parsed = safeParse(trimmed);
    if (!parsed) return [trimmed];
    value = parsed;
  }

  if (Array.isArray(value)) {
    return value
      .filter((item) => String(item || '').trim())
      .map((item) => String(item).trim());
  }

  if (typeof value === 'object') {
    const tables: string[] = [];
    Object.entries(value).forEach(([schemaName, item]) => {
      if (Array.isArray(item)) {
        item.forEach((tableName) => {
          const normalizedTableName = String(tableName || '').trim();
          if (!normalizedTableName) return;
          tables.push(
            schemaName
              ? `${schemaName}.${normalizedTableName}`
              : normalizedTableName,
          );
        });
        return;
      }

      if (typeof item === 'string' && item.trim()) {
        tables.push(schemaName ? `${schemaName}.${item.trim()}` : item.trim());
      }
    });
    return tables;
  }

  return [];
};

const formatTableText = (tableValue: any, fallback: string) => {
  const tables = getTableList(tableValue);
  if (!tables.length) return fallback;
  if (tables.length === 1) return tables[0];
  return `${tables[0]} +${tables.length - 1}`;
};

interface EndpointProps {
  type?: string;
  dataSourceName?: string;
  tableValue?: any;
  mode?: string;
  popoverTitle: string;
}

const Endpoint = ({
  type,
  dataSourceName,
  tableValue,
  mode,
  popoverTitle,
}: EndpointProps) => {
  const intl = useIntl();
  const tables = getTableList(tableValue);
  const fallback = intl.formatMessage({ id: 'pages.batchLinkUp.plan.unconfigured' });
  const tableText = formatTableText(tableValue, fallback);

  const renderTablePopoverContent = () => {
    if (!tables.length) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={intl.formatMessage({
            id: 'pages.batchLinkUp.plan.noTableInfo',
          })}
        />
      );
    }

    return (
      <div className="w-[280px]">
        <div className="mb-2 text-xs text-[#98a2b3]">
          {intl.formatMessage(
            { id: 'pages.batchLinkUp.plan.tableCount' },
            { count: tables.length },
          )}
        </div>

        <div className="flex max-h-[240px] flex-col gap-1 overflow-y-auto">
          {tables.map((tableName, index) => (
            <div
              key={`${tableName}-${index}`}
              title={tableName}
              className="flex min-w-0 items-center gap-2 rounded-md bg-[#f8fafc] px-2 py-1.5 text-xs text-[#475467]"
            >
              <span className="h-1 w-1 shrink-0 rounded-full bg-[#667085]" />
              <span className="truncate">{tableName}</span>
            </div>
          ))}
        </div>
      </div>
    );
  };

  const tableContent: ReactNode =
    mode === 'GUIDE_MULTI' ? (
      <Popover
        placement="rightTop"
        trigger="hover"
        title={popoverTitle}
        content={renderTablePopoverContent()}
      >
        <span
          className={[
            'max-w-[100px] cursor-help truncate',
            tables.length ? 'text-[#475467]' : 'text-[#98a2b3]',
          ].join(' ')}
        >
          {tables.length
            ? intl.formatMessage(
                { id: 'pages.batchLinkUp.plan.selectedTableCount' },
                { count: tables.length },
              )
            : intl.formatMessage({ id: 'pages.batchLinkUp.plan.noTableSelected' })}
        </span>
      </Popover>
    ) : (
      <span
        title={tableText}
        className="max-w-[120px] truncate text-[#667085]"
      >
        {tableText}
      </span>
    );

  return (
    <div className="flex min-w-0 items-center gap-1.5">
      {type ? (
        <span className="flex h-5 w-5 shrink-0 items-center justify-center">
          <DatabaseIcons dbType={type} width="20" height="20" />
        </span>
      ) : (
        <span className="text-[#98a2b3]">-</span>
      )}

      <span
        title={dataSourceName}
        className="max-w-[110px] truncate font-medium text-[#344054]"
      >
        {dataSourceName || type || fallback}
      </span>

      <span className="shrink-0 text-[#d0d5dd]">/</span>
      {tableContent}
    </div>
  );
};

const DataSourceSyncPlan = ({ record }: DataSourceSyncPlanProps) => {
  const intl = useIntl();
  const titleMap: Record<string, string> = {
    GUIDE_SINGLE: intl.formatMessage({ id: 'pages.batchLinkUp.plan.single' }),
    GUIDE_MULTI: intl.formatMessage({ id: 'pages.batchLinkUp.plan.multi' }),
    SCRIPT: intl.formatMessage({ id: 'pages.batchLinkUp.plan.script' }),
  };
  const planTitle =
    record?.jobType !== 'BATCH'
      ? intl.formatMessage({ id: 'pages.batchLinkUp.plan.dataSync' })
      : titleMap[record?.mode] ||
        intl.formatMessage({ id: 'pages.batchLinkUp.plan.batch' });

  return (
    <div className="flex min-w-0 flex-col items-start gap-1.5 text-xs">
      <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[11px] font-medium leading-5 text-[#667085]">
        {planTitle}
      </span>

      <div className="flex min-w-0 items-center gap-2">
        <Endpoint
          type={record?.sourceType}
          dataSourceName={record?.sourceDatasourceName}
          tableValue={record?.sourceTable}
          mode={record?.mode}
          popoverTitle={intl.formatMessage({
            id: 'pages.batchLinkUp.plan.sourceTables',
          })}
        />

        <DoubleRightOutlined className="shrink-0 text-[10px] text-[#98a2b3]" />

        <Endpoint
          type={record?.sinkType}
          dataSourceName={record?.sinkDatasourceName}
          tableValue={record?.sinkTable}
          mode={record?.mode}
          popoverTitle={intl.formatMessage({
            id: 'pages.batchLinkUp.plan.sinkTables',
          })}
        />
      </div>
    </div>
  );
};

export default DataSourceSyncPlan;
