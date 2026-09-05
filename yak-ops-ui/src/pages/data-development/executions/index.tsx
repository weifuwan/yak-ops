import { YakFilterSwitch } from '@/components/ui';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Pagination,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
} from 'antd';
import moment from 'moment';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  getDevelopmentTaskExecution,
  listDevelopmentTaskExecutions,
} from '../service';
import type {
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionStatus,
  DevelopmentTaskExecutionSummary,
  DevelopmentTaskType,
} from '../types';

const { RangePicker } = DatePicker;

const taskTypeOptions = [
  { label: 'SQL', value: 'SQL' },
  { label: 'SHELL', value: 'SHELL' },
  { label: 'PYTHON', value: 'PYTHON' },
  { label: 'JAVA', value: 'JAVA' },
  { label: 'HTTP', value: 'HTTP' },
];

const statusClassName: Record<string, string> = {
  PENDING: 'bg-[#f2f4f7] text-[#667085]',
  RUNNING: 'bg-[#eff8ff] text-[#175cd3]',
  SUCCESS: 'bg-[#ecfdf3] text-[#027a48]',
  FAILED: 'bg-[#fef3f2] text-[#b42318]',
  CANCELLED: 'bg-[#f2f4f7] text-[#667085]',
  TIMEOUT: 'bg-[#fff6ed] text-[#c4320a]',
};

const StatusBadge = ({ status }: { status?: string }) => {
  const intl = useIntl();
  const normalized = String(status || '').toUpperCase();
  const ids: Record<string, string> = {
    PENDING: 'pages.dataDevelopment.execution.pending',
    RUNNING: 'pages.dataDevelopment.execution.running',
    SUCCESS: 'pages.dataDevelopment.execution.success',
    FAILED: 'pages.dataDevelopment.execution.failed',
    CANCELLED: 'pages.dataDevelopment.execution.cancelled',
    TIMEOUT: 'pages.dataDevelopment.execution.timeout',
  };
  return (
    <span
      className={[
        'inline-flex h-6 items-center rounded-md px-2 text-[12px] font-medium',
        statusClassName[normalized] || 'bg-[#f2f4f7] text-[#667085]',
      ].join(' ')}
    >
      {ids[normalized] ? intl.formatMessage({ id: ids[normalized] }) : normalized || '-'}
    </span>
  );
};

const formatDuration = (duration?: number | null) => {
  if (duration === null || duration === undefined) return '-';
  if (duration < 1000) return `${duration} ms`;
  if (duration < 60_000) {
    return `${(duration / 1000).toFixed(duration < 10_000 ? 2 : 1)} s`;
  }
  return `${(duration / 60_000).toFixed(1)} min`;
};

const ExecutionHistoryPage = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const text = useCallback(
    (id: string) => intlRef.current.formatMessage({ id }),
    [],
  );

  const [records, setRecords] = useState<DevelopmentTaskExecutionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<DevelopmentTaskExecutionStatus | undefined>();
  const [taskType, setTaskType] = useState<DevelopmentTaskType | undefined>();
  const [triggerType, setTriggerType] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<any>();
  const [keyword, setKeyword] = useState('');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<DevelopmentTaskExecutionDetail>();

  const loadRecords = useCallback(async () => {
    setLoading(true);
    try {
      const response = await listDevelopmentTaskExecutions({
        pageNo,
        pageSize,
        keyword: keyword || undefined,
        status,
        taskType,
        triggerType,
        startTime: dateRange?.[0]?.format?.('YYYY-MM-DD 00:00:00'),
        endTime: dateRange?.[1]?.format?.('YYYY-MM-DD 23:59:59'),
      });
      setRecords(response.data?.records || []);
      setTotal(response.data?.total || 0);
    } catch {
      message.error(text('pages.dataDevelopment.execution.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [dateRange, keyword, pageNo, pageSize, refreshKey, status, taskType, text, triggerType]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  const applyStatus = (value?: DevelopmentTaskExecutionStatus) => {
    setStatus(value);
    setPageNo(1);
  };

  const search = () => {
    setKeyword(keywordDraft.trim());
    setPageNo(1);
  };

  const reset = () => {
    setKeyword('');
    setKeywordDraft('');
    setStatus(undefined);
    setTaskType(undefined);
    setTriggerType(undefined);
    setDateRange(undefined);
    setPageNo(1);
  };

  const openDetail = async (record: DevelopmentTaskExecutionSummary) => {
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      const response = await getDevelopmentTaskExecution(record.id);
      setDetail(response.data);
    } catch {
      message.error(text('pages.dataDevelopment.execution.detailFailed'));
    } finally {
      setDetailLoading(false);
    }
  };

  const triggerLabel = (value?: string) => {
    if (value === 'MANUAL') return intl.formatMessage({ id: 'pages.dataDevelopment.execution.manual' });
    if (value === 'WORKFLOW') return intl.formatMessage({ id: 'pages.dataDevelopment.execution.workflow' });
    if (value === 'SCHEDULE') return intl.formatMessage({ id: 'pages.dataDevelopment.execution.schedule' });
    return value || '-';
  };

  const statusTabs = [
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.all' }), value: 'ALL' },
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.running' }), value: 'RUNNING' },
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.success' }), value: 'SUCCESS' },
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.failed' }), value: 'FAILED' },
  ];

  const triggerOptions = [
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.manual' }), value: 'MANUAL' },
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.workflow' }), value: 'WORKFLOW' },
    { label: intl.formatMessage({ id: 'pages.dataDevelopment.execution.schedule' }), value: 'SCHEDULE' },
  ];

  const columns = [
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.execution.taskAndNode' }),
      dataIndex: 'taskName',
      width: 240,
      render: (_: unknown, record: DevelopmentTaskExecutionSummary) => (
        <div className="min-w-0 py-0.5">
          <button
            type="button"
            className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#344054] hover:text-[#161823]"
            title={record.taskName}
            onClick={() => history.push('/data-development')}
          >
            {record.taskName || '-'}
          </button>
          <div className="mt-0.5 truncate text-[11px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.common.nodeId' })}: {record.nodeId}
          </div>
        </div>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.type' }),
      dataIndex: 'taskType',
      width: 90,
      render: (value: string) => (
        <span className="text-[12px] font-medium text-[#475467]">{value || '-'}</span>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.execution.trigger' }),
      dataIndex: 'triggerType',
      width: 105,
      render: (value: string) => <span className="text-[12px] text-[#667085]">{triggerLabel(value)}</span>,
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.status' }),
      dataIndex: 'status',
      width: 100,
      align: 'center' as const,
      render: (value: string) => <StatusBadge status={value} />,
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.runtimeExecution' }),
      dataIndex: 'runtimeExecutionId',
      width: 205,
      ellipsis: true,
      render: (value?: string | null) => (
        <Tooltip title={value || undefined}>
          <span className="font-mono text-[11px] text-[#667085]">{value || '-'}</span>
        </Tooltip>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.operator' }),
      dataIndex: 'operatorName',
      width: 120,
      ellipsis: true,
      render: (value?: string | null) => <span className="text-[12px] text-[#667085]">{value || '-'}</span>,
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.duration' }),
      dataIndex: 'durationMs',
      width: 105,
      align: 'right' as const,
      render: (value?: number | null) => <span className="text-[12px] text-[#667085]">{formatDuration(value)}</span>,
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.startTime' }),
      dataIndex: 'startTime',
      width: 170,
      render: (value?: string | null) => (
        <span className="whitespace-nowrap text-[12px] text-[#98a2b3]">
          {value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '-'}
        </span>
      ),
    },
    {
      title: intl.formatMessage({ id: 'pages.dataDevelopment.common.action' }),
      key: 'action',
      width: 100,
      fixed: 'right' as const,
      render: (_: unknown, record: DevelopmentTaskExecutionSummary) => (
        <Button
          type="link"
          size="small"
          className="!px-0 !text-[12px] !text-[#475467]"
          onClick={() => void openDetail(record)}
        >
          {intl.formatMessage({ id: 'pages.dataDevelopment.common.detail' })}
        </Button>
      ),
    },
  ];

  return (
    <ConfigProvider
      theme={{
        token: { borderRadius: 8, colorBorder: '#f0f0f0', colorBgContainer: '#ffffff' },
        components: {
          Input: { activeShadow: 'none' },
          Select: { activeOutlineColor: 'transparent' },
        },
      }}
    >
      <div className="flex min-h-[calc(100vh-64px)] flex-col bg-white px-5 pt-4">
        <div className="flex items-center justify-between">
          <h1 className="m-0 text-[17px] font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.execution.title' })}
          </h1>
          <Button icon={<ReloadOutlined />} onClick={() => setRefreshKey((value) => value + 1)}>
            {intl.formatMessage({ id: 'pages.dataDevelopment.common.refresh' })}
          </Button>
        </div>

        <div className="mt-3 border-b border-[#f0f0f0]">
          <div className="flex min-h-[54px] items-center justify-between gap-4 py-2">
            <YakFilterSwitch
              value={status || 'ALL'}
              options={statusTabs}
              onChange={(value) =>
                applyStatus(
                  value === 'ALL' ? undefined : (value as DevelopmentTaskExecutionStatus),
                )
              }
            />

            <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
              <Input
                allowClear
                variant="filled"
                value={keywordDraft}
                prefix={<SearchOutlined className="text-[#98a2b3]" />}
                placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.execution.searchPlaceholder' })}
                className="!h-9 !w-[230px] !min-w-[190px]"
                onChange={(event) => setKeywordDraft(event.target.value)}
                onPressEnter={search}
              />
              <Select
                allowClear
                variant="filled"
                value={taskType}
                options={taskTypeOptions}
                placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskType' })}
                className="!h-9 !w-[130px] !min-w-[120px]"
                onChange={(value) => {
                  setTaskType(value);
                  setPageNo(1);
                }}
              />
              <Select
                allowClear
                variant="filled"
                value={triggerType}
                options={triggerOptions}
                placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.execution.triggerPlaceholder' })}
                className="!h-9 !w-[130px] !min-w-[120px]"
                onChange={(value) => {
                  setTriggerType(value);
                  setPageNo(1);
                }}
              />
              <RangePicker
                allowClear
                variant="filled"
                value={dateRange}
                format="YYYY-MM-DD"
                placeholder={[
                  intl.formatMessage({ id: 'pages.dataDevelopment.execution.startDate' }),
                  intl.formatMessage({ id: 'pages.dataDevelopment.execution.endDate' }),
                ]}
                className="!h-9 !w-[250px] !min-w-[230px]"
                onChange={(value) => {
                  setDateRange(value);
                  setPageNo(1);
                }}
              />
              <Button type="primary" onClick={search}>
                {intl.formatMessage({ id: 'pages.dataDevelopment.common.search' })}
              </Button>
              <Button onClick={reset}>
                {intl.formatMessage({ id: 'pages.dataDevelopment.common.reset' })}
              </Button>
            </div>
          </div>
        </div>

        <div className="min-h-0 flex-1 pt-4">
          <Table
            rowKey="id"
            size="small"
            bordered
            loading={loading}
            columns={columns}
            dataSource={records}
            pagination={false}
            scroll={{ x: 1320, y: 'calc(100vh - 300px)' }}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={intl.formatMessage({ id: 'pages.dataDevelopment.execution.empty' })}
                />
              ),
            }}
          />
        </div>

        <div className="flex h-16 shrink-0 items-center justify-between border-t border-[#f0f0f0]">
          <span className="text-[12px] text-[#98a2b3]">
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.execution.total' },
              { count: total },
            )}
          </span>
          <Pagination
            current={pageNo}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            showQuickJumper
            pageSizeOptions={[10, 20, 50, 100]}
            onChange={(page, size) => {
              setPageNo(size === pageSize ? page : 1);
              setPageSize(size);
            }}
          />
        </div>
      </div>

      <Drawer
        title={intl.formatMessage({ id: 'pages.dataDevelopment.execution.detailTitle' })}
        placement="right"
        width={720}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
      >
        {detailLoading ? (
          <div className="flex h-64 items-center justify-center"><Spin size="small" /></div>
        ) : detail ? (
          <div className="space-y-6">
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskName' })}>{detail.taskName}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.nodeId' })}>{detail.nodeId}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskType' })}>{detail.taskType}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.status' })}><StatusBadge status={detail.status} /></Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.execution.trigger' })}>{triggerLabel(detail.triggerType)}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.operator' })}>{detail.operatorName || '-'}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.runtimeExecution' })} span={2}>{detail.runtimeExecutionId || '-'}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.startTime' })}>{detail.startTime ? moment(detail.startTime).format('YYYY-MM-DD HH:mm:ss') : '-'}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.duration' })}>{formatDuration(detail.durationMs)}</Descriptions.Item>
            </Descriptions>

            {detail.errorMessage ? (
              <section>
                <div className="mb-2 text-[13px] font-semibold text-[#344054]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.execution.error' })}
                </div>
                <div className="rounded-md bg-[#fef3f2] px-3 py-2 text-[12px] leading-5 text-[#b42318]">
                  {detail.errorMessage}
                </div>
              </section>
            ) : null}

            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.content' })}
              </div>
              <pre className="max-h-[280px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{detail.content || '-'}</pre>
            </section>

            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.config' })}
              </div>
              <pre className="max-h-[220px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{detail.configJson || '{}'}</pre>
            </section>

            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.output' })}
              </div>
              <pre className="max-h-[360px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{JSON.stringify(detail.output || {}, null, 2)}</pre>
            </section>
          </div>
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={intl.formatMessage({ id: 'pages.dataDevelopment.execution.detailEmpty' })}
          />
        )}
      </Drawer>
    </ConfigProvider>
  );
};

export default ExecutionHistoryPage;
