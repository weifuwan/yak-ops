import YakButton from '@/components/YakButton';
import YakOpsEmpty from '@/components/YakOpsEmpty';
import { useIntl } from '@umijs/max';
import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useMemo, type MouseEvent } from 'react';
import {
  CheckResultTag,
  ExecutionStatusTag,
} from '../../components/QualityStatus';
import { formatQualityDimension } from '../../i18n';
import type {
  ExecutionWorkspaceListItem,
  RuleExecutionWorkspaceListItem,
} from '../types';

export type ExecutionViewMode = 'EXECUTION' | 'RULE';

interface Props {
  executionRecords: ExecutionWorkspaceListItem[];
  ruleRecords: RuleExecutionWorkspaceListItem[];
  loading: boolean;
  mode: ExecutionViewMode;
  onOpenExecution: (executionNo: string) => void;
  onOpenMonitor: (monitorId: number) => void;
}

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';

const issueCount = (record: ExecutionWorkspaceListItem) =>
  record.failedRules + record.errorRules;

const TABLE_CLASS_NAME = [
  'compact-sync-task-table',
  '[&_.ant-table]:!text-[13px]',
  '[&_.ant-table-container]:!border-[#eaecf0]',
  '[&_.ant-table-cell]:!align-middle',
  '[&_.ant-table-thead>tr>th]:!h-10',
  '[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb]',
  '[&_.ant-table-thead>tr>th]:!px-4',
  '[&_.ant-table-thead>tr>th]:!py-2',
  '[&_.ant-table-thead>tr>th]:!text-[12px]',
  '[&_.ant-table-thead>tr>th]:!font-medium',
  '[&_.ant-table-thead>tr>th]:!text-[#667085]',
  '[&_.ant-table-thead>tr>th]:!border-[#eaecf0]',
  '[&_.ant-table-tbody>tr>td]:!px-4',
  '[&_.ant-table-tbody>tr>td]:!py-2.5',
  '[&_.ant-table-tbody>tr>td]:!border-[#f0f2f5]',
  '[&_.ant-table-tbody>tr>td]:!text-[#667085]',
  '[&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc]',
  '[&_.ant-table-cell-fix-right]:!bg-white',
  '[&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc]',
  '[&_.ant-checkbox-inner]:!h-4',
  '[&_.ant-checkbox-inner]:!w-4',
  '[&_.ant-table-placeholder>td]:!h-[240px]',
].join(' ');

const ExecutionRecordTable = ({
  executionRecords,
  ruleRecords,
  loading,
  mode,
  onOpenExecution,
  onOpenMonitor,
}: Props) => {
  const intl = useIntl();
  const triggerLabel = (value: ExecutionWorkspaceListItem['triggerType']) =>
    intl.formatMessage({
      id:
        value === 'SCHEDULE'
          ? 'pages.dataQuality.common.trigger.schedule'
          : 'pages.dataQuality.common.trigger.manual',
    });
  const scopeLabel = (value: RuleExecutionWorkspaceListItem['scope']) =>
    intl.formatMessage({
      id:
        value === 'TABLE'
          ? 'pages.dataQuality.common.scope.table'
          : 'pages.dataQuality.common.scope.column',
    });

  const executionColumns = useMemo<ColumnsType<ExecutionWorkspaceListItem>>(
    () => [
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.execution',
        }),
        width: 270,
        fixed: 'left',
        render: (_, record) => (
          <div className="min-w-0 py-0.5">
            <div className="truncate text-[11px] text-[#98a2b3]">
              {record.executionNo}
            </div>
            <YakButton
              type="text"
              htmlType="button"
              className="mt-1 !block !h-auto max-w-full !min-h-0 !cursor-pointer !truncate !border-0 !bg-transparent !p-0 !text-left !font-medium !text-[#fe2c55]"
              onClick={(event: MouseEvent<HTMLButtonElement>) => {
                event.stopPropagation();
                onOpenExecution(record.executionNo);
              }}
            >
              {record.monitorName}
            </YakButton>
          </div>
        ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.object',
        }),
        width: 250,
        render: (_, record) => (
          <div className="min-w-0 py-0.5">
            <div className="truncate font-medium text-[#344054]">
              {record.objectName}
            </div>
            <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
              {intl.formatMessage(
                { id: 'pages.dataQuality.execution.sourcePrefix' },
                { name: record.dataSourceName },
              )}
            </div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.status',
        }),
        dataIndex: 'executionStatus',
        width: 110,
        render: (value) => <ExecutionStatusTag value={value} />,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.result',
        }),
        dataIndex: 'checkResult',
        width: 110,
        render: (value) => <CheckResultTag value={value} />,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.issueCount',
        }),
        width: 100,
        render: (_, record) =>
          issueCount(record) > 0 ? (
            <Tag color="error" className="!m-0">
              {issueCount(record)}
            </Tag>
          ) : (
            <span className="text-[#98a2b3]">0</span>
          ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.ruleSummary',
        }),
        width: 260,
        render: (_, record) => (
          <div className="flex items-center gap-3 text-xs">
            <span className="text-[#667085]">
              {intl.formatMessage(
                { id: 'pages.dataQuality.execution.summary.total' },
                { count: record.totalRules },
              )}
            </span>
            <span className="text-[#245bdb]">
              {intl.formatMessage(
                { id: 'pages.dataQuality.execution.summary.passed' },
                { count: record.passedRules },
              )}
            </span>
            <span className="text-[#d92d20]">
              {intl.formatMessage(
                { id: 'pages.dataQuality.execution.summary.failed' },
                { count: record.failedRules },
              )}
            </span>
            <span className="text-[#b54708]">
              {intl.formatMessage(
                { id: 'pages.dataQuality.execution.summary.error' },
                { count: record.errorRules },
              )}
            </span>
          </div>
        ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.trigger',
        }),
        width: 200,
        render: (_, record) => (
          <div className="space-y-1 text-xs">
            <div className="text-[#344054]">
              {triggerLabel(record.triggerType)} · {record.operator || 'system'}
            </div>
            <div className="text-[#98a2b3]">
              {formatTime(record.startedAt || record.queuedAt)}
            </div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.finishedAt',
        }),
        dataIndex: 'finishedAt',
        width: 170,
        render: formatTime,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.actions',
        }),
        width: 120,
        fixed: 'right',
        render: (_, record) => (
          <div className="flex items-center">
            <YakButton
              type="text"
              size="small"
              className="!text-[#667085]"
              onClick={(event) => {
                event.stopPropagation();
                onOpenExecution(record.executionNo);
              }}
            >
              {intl.formatMessage({ id: 'pages.dataQuality.common.details' })}
            </YakButton>
            <YakButton
              type="text"
              size="small"
              className="!text-[#667085]"
              onClick={(event) => {
                event.stopPropagation();
                onOpenMonitor(record.monitorId);
              }}
            >
              {intl.formatMessage({ id: 'pages.dataQuality.common.rules' })}
            </YakButton>
          </div>
        ),
      },
    ],
    [intl, onOpenExecution, onOpenMonitor],
  );

  const ruleColumns = useMemo<ColumnsType<RuleExecutionWorkspaceListItem>>(
    () => [
      {
        title: intl.formatMessage({ id: 'pages.dataQuality.execution.column.rule' }),
        width: 270,
        fixed: 'left',
        render: (_, record) => (
          <div className="min-w-0 py-0.5">
            <div className="truncate text-[11px] text-[#98a2b3]">
              {record.ruleId} · {record.executionNo}
            </div>
            <YakButton
              type="text"
              htmlType="button"
              className="mt-1 !block !h-auto max-w-full !min-h-0 !cursor-pointer !truncate !border-0 !bg-transparent !p-0 !text-left !font-medium !text-[#fe2c55]"
              onClick={(event: MouseEvent<HTMLButtonElement>) => {
                event.stopPropagation();
                onOpenExecution(record.executionNo);
              }}
            >
              {record.ruleName}
            </YakButton>
          </div>
        ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.dimension',
        }),
        dataIndex: 'dimension',
        width: 125,
        render: (value: string) => formatQualityDimension(intl, value),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.status',
        }),
        dataIndex: 'executionStatus',
        width: 110,
        render: (value) => <ExecutionStatusTag value={value} />,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.issueHandling',
        }),
        width: 120,
        render: (_, record) =>
          ['NOT_PASSED', 'ERROR'].includes(record.checkResult) ? (
            <span className="text-[#d92d20]">
              {intl.formatMessage({ id: 'pages.dataQuality.execution.issueExists' })}
            </span>
          ) : (
            <span className="text-[#98a2b3]">-</span>
          ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.finishedAt',
        }),
        dataIndex: 'finishedAt',
        width: 170,
        render: formatTime,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.tableName',
        }),
        width: 180,
        render: (_, record) => (
          <div className="min-w-0">
            <div className="truncate text-[#344054]">{record.tableName}</div>
            <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
              {record.dataSourceName}
            </div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.dataQuality.execution.column.scope' }),
        dataIndex: 'scope',
        width: 110,
        render: (value) => (
          <Tag className="!m-0 !border-0 !bg-[#fff0f3] !text-[#fe2c55]">
            {scopeLabel(value)}
          </Tag>
        ),
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.template',
        }),
        dataIndex: 'templateCode',
        width: 170,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.importance',
        }),
        width: 110,
        render: () => <span className="text-[#98a2b3]">--</span>,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.expected',
        }),
        dataIndex: 'expectedValue',
        width: 180,
        render: (value) => value || '--',
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.metric',
        }),
        dataIndex: 'metricValue',
        width: 150,
        render: (value) => value || '--',
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.result',
        }),
        dataIndex: 'checkResult',
        width: 110,
        render: (value) => <CheckResultTag value={value} />,
      },
      {
        title: intl.formatMessage({
          id: 'pages.dataQuality.execution.column.actions',
        }),
        width: 120,
        fixed: 'right',
        render: (_, record) => (
          <div className="flex items-center">
            <YakButton
              type="text"
              size="small"
              className="!text-[#667085]"
              onClick={(event) => {
                event.stopPropagation();
                onOpenExecution(record.executionNo);
              }}
            >
              {intl.formatMessage({ id: 'pages.dataQuality.common.details' })}
            </YakButton>
            <YakButton
              type="text"
              size="small"
              className="!text-[#667085]"
              onClick={(event) => {
                event.stopPropagation();
                onOpenMonitor(record.monitorId);
              }}
            >
              {intl.formatMessage({ id: 'pages.dataQuality.common.rules' })}
            </YakButton>
          </div>
        ),
      },
    ],
    [intl, onOpenExecution, onOpenMonitor],
  );

  const emptyText = (
    <div className="flex min-h-[220px] items-center justify-center">
      <YakOpsEmpty
        width={176}
        height={120}
        title={intl.formatMessage({ id: 'pages.dataQuality.execution.empty' })}
        description={intl.formatMessage({
          id: 'pages.dataQuality.execution.emptyDesc',
        })}
      />
    </div>
  );

  if (mode === 'RULE') {
    return (
      <Table<RuleExecutionWorkspaceListItem>
        rowKey={(record) => `${record.executionNo}-${record.id}`}
        size="small"
        bordered
        loading={loading}
        pagination={false}
        scroll={{ x: 1900 }}
        dataSource={ruleRecords}
        columns={ruleColumns}
        locale={{ emptyText }}
        className={TABLE_CLASS_NAME}
        onRow={(record) => ({
          onClick: () => onOpenExecution(record.executionNo),
          className: 'cursor-pointer',
        })}
      />
    );
  }

  return (
    <Table<ExecutionWorkspaceListItem>
      rowKey="executionNo"
      size="small"
      bordered
      loading={loading}
      pagination={false}
      className={TABLE_CLASS_NAME}
      scroll={{ x: 1460 }}
      dataSource={executionRecords}
      columns={executionColumns}
      locale={{ emptyText }}
      onRow={(record) => ({
        onClick: () => onOpenExecution(record.executionNo),
        className: 'cursor-pointer',
      })}
    />
  );
};

export default ExecutionRecordTable;
