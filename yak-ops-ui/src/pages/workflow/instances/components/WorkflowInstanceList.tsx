import {
  YakButton,
  YakFilterSwitch,
  YakStatusIcon,
  type YakStatus,
} from '@/components/ui';
import {
  batchRetryWorkflowInstances,
  getWorkflowInstances,
  isWorkflowTerminal,
  type WorkflowInstance,
} from '@/services/workflow';
import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import {
  ConfigProvider,
  DatePicker,
  Empty,
  Input,
  Modal,
  Pagination,
  Popover,
  Select,
  Table,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState, type Key } from 'react';

const { RangePicker } = DatePicker;

interface WorkflowStatusMeta {
  messageId?: string;
  yakStatus: YakStatus;
  animated?: boolean;
}

const WORKFLOW_STATUS_META: Record<string, WorkflowStatusMeta> = {
  CREATED: { messageId: 'pages.workflow.status.runtime.created', yakStatus: 'pending' },
  RUNNING: { messageId: 'pages.workflow.status.runtime.running', yakStatus: 'running', animated: true },
  PAUSING: { messageId: 'pages.workflow.status.runtime.pausing', yakStatus: 'running', animated: true },
  PAUSED: { messageId: 'pages.workflow.status.runtime.paused', yakStatus: 'paused' },
  RESUMING: { messageId: 'pages.workflow.status.runtime.resuming', yakStatus: 'running', animated: true },
  SUCCESS: { messageId: 'pages.workflow.status.runtime.success', yakStatus: 'success' },
  SUCCESS_WITH_WARNINGS: { messageId: 'pages.workflow.status.runtime.successWithWarnings', yakStatus: 'warning' },
  FAILED: { messageId: 'pages.workflow.status.runtime.failed', yakStatus: 'failed' },
  WARNING: { messageId: 'pages.workflow.status.runtime.warning', yakStatus: 'warning' },
  CANCELED: { messageId: 'pages.workflow.status.runtime.canceled', yakStatus: 'canceled' },
  TIMED_OUT: { messageId: 'pages.workflow.status.runtime.timedOut', yakStatus: 'failed' },
};

const RUNNING_STATUSES = new Set(['CREATED', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING']);
const COMPLETED_STATUSES = new Set(['SUCCESS', 'SUCCESS_WITH_WARNINGS', 'WARNING', 'CANCELED']);
const FAILED_STATUSES = new Set(['FAILED', 'TIMED_OUT']);
const RETRYABLE_NODE_STATUSES = new Set(['FAILED', 'UPSTREAM_FAILED', 'SKIPPED', 'CANCELED']);

type StatusGroup = 'ALL' | 'RUNNING' | 'COMPLETED' | 'FAILED';
type TestRunFilter = 'ALL' | 'TRUE' | 'FALSE';

const STATUS_TABS: Array<{ value: StatusGroup; messageId: string }> = [
  { value: 'ALL', messageId: 'pages.workflow.instance.filter.all' },
  { value: 'RUNNING', messageId: 'pages.workflow.instance.filter.running' },
  { value: 'COMPLETED', messageId: 'pages.workflow.instance.filter.completed' },
  { value: 'FAILED', messageId: 'pages.workflow.instance.filter.failed' },
];

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';

const isRetryableInstance = (instance: WorkflowInstance) =>
  isWorkflowTerminal(instance.status) &&
  instance.status !== 'SUCCESS' &&
  instance.nodes.some((node) => RETRYABLE_NODE_STATUSES.has(node.status));

const WorkflowInstancesPage = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const [instances, setInstances] = useState<WorkflowInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusGroup, setStatusGroup] = useState<StatusGroup>('ALL');
  const [keyword, setKeyword] = useState('');
  const [keywordDraft, setKeywordDraft] = useState('');
  const [dateRange, setDateRange] = useState<Dayjs[]>();
  const [instanceIdFilter, setInstanceIdFilter] = useState('');
  const [definitionIdFilter, setDefinitionIdFilter] = useState('');
  const [testRunFilter, setTestRunFilter] = useState<TestRunFilter>('ALL');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([]);
  const [batchLoading, setBatchLoading] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const load = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setInstances(await getWorkflowInstances());
    } catch (error) {
      if (showLoading) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({ id: 'pages.workflow.instance.loadFailed' }),
        );
      }
    } finally {
      if (showLoading) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(true);
    const timer = window.setInterval(() => void load(false), 2500);
    return () => window.clearInterval(timer);
  }, [load]);

  const matchesGroup = (status: string) => {
    if (statusGroup === 'ALL') return true;
    if (statusGroup === 'RUNNING') return RUNNING_STATUSES.has(status);
    if (statusGroup === 'COMPLETED') return COMPLETED_STATUSES.has(status);
    return FAILED_STATUSES.has(status);
  };

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    const instanceId = instanceIdFilter.trim().toLowerCase();
    const definitionId = definitionIdFilter.trim().toLowerCase();
    const [start, end] = dateRange || [];
    return instances.filter((record) => {
      if (!matchesGroup(record.status)) return false;
      if (q && !(record.name || '').toLowerCase().includes(q)) return false;
      if (instanceId && !record.id.toLowerCase().includes(instanceId)) return false;
      if (definitionId && !record.definitionId?.toLowerCase().includes(definitionId)) return false;
      if (testRunFilter === 'TRUE' && !record.testRun) return false;
      if (testRunFilter === 'FALSE' && record.testRun) return false;
      if (start && dayjs(record.startedAt).isBefore(start.startOf('day'))) return false;
      if (end && dayjs(record.startedAt).isAfter(end.endOf('day'))) return false;
      return true;
    });
  }, [dateRange, definitionIdFilter, instanceIdFilter, instances, keyword, statusGroup, testRunFilter]);

  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filtered.length / pageSize));
    if (page > maxPage) setPage(maxPage);
  }, [filtered.length, page, pageSize]);

  const pageData = useMemo(() => {
    const offset = (page - 1) * pageSize;
    return filtered.slice(offset, offset + pageSize);
  }, [filtered, page, pageSize]);

  const advancedFilterCount = [
    instanceIdFilter.trim(),
    definitionIdFilter.trim(),
    testRunFilter === 'ALL' ? '' : testRunFilter,
  ].filter(Boolean).length;

  const hasActiveFilters =
    statusGroup !== 'ALL' ||
    Boolean(keyword.trim()) ||
    Boolean(dateRange?.length) ||
    advancedFilterCount > 0;

  const resetAdvancedFilters = () => {
    setInstanceIdFilter('');
    setDefinitionIdFilter('');
    setTestRunFilter('ALL');
    setPage(1);
  };

  const resetAllFilters = () => {
    setStatusGroup('ALL');
    setKeyword('');
    setKeywordDraft('');
    setDateRange(undefined);
    resetAdvancedFilters();
    setSelectedKeys([]);
  };

  const handleSearch = () => {
    setKeyword(keywordDraft.trim());
    setPage(1);
    setSelectedKeys([]);
  };

  const openDetail = (record: WorkflowInstance) => {
    history.push(`/workflow/instances/${encodeURIComponent(record.id)}`);
  };

  const formatDuration = (record: WorkflowInstance) => {
    if (!record.startedAt) return '-';
    const seconds = Math.max(
      0,
      dayjs(record.endedAt || undefined).diff(dayjs(record.startedAt), 'second'),
    );
    if (seconds < 60) {
      return intl.formatMessage(
        { id: 'pages.workflow.instance.durationSeconds' },
        { seconds },
      );
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return intl.formatMessage(
        { id: 'pages.workflow.instance.durationMinutesSeconds' },
        { minutes, seconds: seconds % 60 },
      );
    }
    return intl.formatMessage(
      { id: 'pages.workflow.instance.durationHoursMinutes' },
      { hours: Math.floor(minutes / 60), minutes: minutes % 60 },
    );
  };

  const batchRetry = async () => {
    if (!selectedKeys.length || batchLoading) return;
    setBatchLoading(true);
    try {
      const result = await batchRetryWorkflowInstances(selectedKeys.map(String));
      setSelectedKeys([]);
      await load(false);
      if (result.failedCount === 0) {
        message.success(
          intlRef.current.formatMessage(
            { id: 'pages.workflow.instance.batchRetrySuccess' },
            { count: result.acceptedCount },
          ),
        );
      } else {
        Modal.info({
          title: intlRef.current.formatMessage(
            { id: 'pages.workflow.instance.batchRetryResult' },
            { success: result.acceptedCount, failed: result.failedCount },
          ),
          width: 680,
          content: (
            <div className="mt-3 max-h-[320px] space-y-2 overflow-auto">
              {result.items
                .filter((item) => !item.accepted)
                .map((item) => (
                  <div
                    key={item.executionId}
                    className="rounded-md border border-[#fecdca] bg-[#fff6f5] px-3 py-2 text-[12px]"
                  >
                    <div className="font-mono text-[#b42318]">{item.executionId}</div>
                    <div className="mt-1 text-[#667085]">
                      {item.message ||
                        intlRef.current.formatMessage({ id: 'pages.workflow.instance.retryFailed' })}
                    </div>
                  </div>
                ))}
            </div>
          ),
        });
      }
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.instance.batchRetryFailed' }),
      );
    } finally {
      setBatchLoading(false);
    }
  };

  const columns = useMemo<ColumnsType<WorkflowInstance>>(
    () => [
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.name' }),
        dataIndex: 'name',
        width: 300,
        render: (_: unknown, record) => (
          <div className="truncate text-[13px] font-medium leading-5 text-[#344054]" title={record.name || undefined}>
            {record.name || '-'}
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.creator' }),
        dataIndex: 'creatorName',
        width: 160,
        render: (value?: string) => (
          <span className="block truncate text-[12px] text-[#667085]" title={value || undefined}>
            {value || '-'}
          </span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.status' }),
        dataIndex: 'status',
        width: 150,
        align: 'center',
        render: (status: string) => {
          const meta: WorkflowStatusMeta = WORKFLOW_STATUS_META[status] || { yakStatus: 'unknown' };
          const label = meta.messageId
            ? intl.formatMessage({ id: meta.messageId })
            : status || '-';
          return (
            <span className="inline-flex min-h-6 items-center justify-center gap-1.5 whitespace-nowrap text-[12px] font-medium text-[#475467]">
              <YakStatusIcon status={meta.yakStatus} size={17} animated={Boolean(meta.animated)} />
              <span>{label}</span>
            </span>
          );
        },
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.summary' }),
        width: 220,
        render: (_: unknown, record) => (
          <div className="text-[12px] leading-5 text-[#667085]">
            <div>
              {intl.formatMessage(
                { id: 'pages.workflow.instance.nodesEdges' },
                { nodes: record.nodeCount, edges: record.edgeCount },
              )}
            </div>
            <div className="text-[#98a2b3]">
              {intl.formatMessage(
                { id: 'pages.workflow.instance.duration' },
                { duration: formatDuration(record) },
              )}
            </div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.startedAt' }),
        dataIndex: 'startedAt',
        width: 190,
        render: (value?: string) => (
          <span className="whitespace-nowrap text-[12px] text-[#667085]">{formatTime(value)}</span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.endedAt' }),
        dataIndex: 'endedAt',
        width: 190,
        render: (value?: string) => (
          <span className="whitespace-nowrap text-[12px] text-[#667085]">{formatTime(value)}</span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instance.actions' }),
        width: 100,
        fixed: 'right',
        render: (_: unknown, record) => (
          <YakButton
            type="text"
            size="small"
            className="!h-7 !px-2 !text-[12px] !text-[#475467]"
            onClick={() => openDetail(record)}
          >
            {intl.formatMessage({ id: 'pages.workflow.instance.operations' })}
          </YakButton>
        ),
      },
    ],
    [intl],
  );

  const advancedFilters = (
    <div className="w-[360px]">
      <div className="mb-4">
        <div className="text-[14px] font-semibold text-[#101828]">
          {intl.formatMessage({ id: 'pages.workflow.instance.advanced' })}
        </div>
        <div className="mt-1 text-[12px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.workflow.instance.advancedHint' })}
        </div>
      </div>
      <div className="space-y-4">
        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">
            {intl.formatMessage({ id: 'pages.workflow.instance.instanceId' })}
          </div>
          <Input
            allowClear
            variant="filled"
            value={instanceIdFilter}
            onChange={(event) => {
              setInstanceIdFilter(event.target.value);
              setPage(1);
              setSelectedKeys([]);
            }}
            placeholder={intl.formatMessage({ id: 'pages.workflow.instance.instanceIdPlaceholder' })}
          />
        </div>
        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">
            {intl.formatMessage({ id: 'pages.workflow.instance.definitionId' })}
          </div>
          <Input
            allowClear
            variant="filled"
            value={definitionIdFilter}
            onChange={(event) => {
              setDefinitionIdFilter(event.target.value);
              setPage(1);
              setSelectedKeys([]);
            }}
            placeholder="Workflow Version / Runtime Definition"
          />
        </div>
        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">
            {intl.formatMessage({ id: 'pages.workflow.instance.runType' })}
          </div>
          <Select
            className="w-full"
            variant="filled"
            value={testRunFilter}
            onChange={(value) => {
              setTestRunFilter(value);
              setPage(1);
              setSelectedKeys([]);
            }}
            options={[
              { value: 'ALL', label: intl.formatMessage({ id: 'pages.workflow.instance.runType.all' }) },
              { value: 'FALSE', label: intl.formatMessage({ id: 'pages.workflow.instance.runType.production' }) },
              { value: 'TRUE', label: intl.formatMessage({ id: 'pages.workflow.instance.runType.test' }) },
            ]}
          />
        </div>
      </div>
      <div className="mt-5 flex justify-end border-t border-[#f0f0f0] pt-4">
        <YakButton disabled={advancedFilterCount === 0} onClick={resetAdvancedFilters}>
          {intl.formatMessage({ id: 'pages.workflow.common.reset' })}
        </YakButton>
      </div>
    </div>
  );

  return (
    <ConfigProvider
      theme={{
        token: { borderRadius: 10, colorBorder: '#f0f0f0', colorBgContainer: '#ffffff' },
        components: {
          Button: { borderRadius: 8 },
          Input: { activeShadow: 'none' },
          Select: { activeOutlineColor: 'transparent' },
        },
      }}
    >
      <div className="flex h-[calc(100vh-64px)] min-h-0 flex-col overflow-hidden bg-white px-5 pt-4 text-[#161823]">
        <div className="mb-2 shrink-0">
          <h1 className="m-0 text-[17px] font-semibold leading-7">
            {intl.formatMessage({ id: 'pages.workflow.instance.title' })}
          </h1>
        </div>

        <div className="mx-auto flex min-h-0 w-full max-w-full flex-1 flex-col">
          <div className="flex min-h-[52px] shrink-0 items-center justify-between gap-4 border-b border-[#eceef2]">
            <YakFilterSwitch
              value={statusGroup}
              options={STATUS_TABS.map((item) => ({
                value: item.value,
                label: intl.formatMessage({ id: item.messageId }),
              }))}
              onChange={(value) => {
                setStatusGroup(value);
                setPage(1);
                setSelectedKeys([]);
              }}
            />
            <div className="flex min-w-0 flex-1 items-center justify-end gap-2 overflow-x-auto">
              <Input
                allowClear
                variant="filled"
                prefix={<SearchOutlined className="text-[#98a2b3]" />}
                placeholder={intl.formatMessage({ id: 'pages.workflow.instance.searchPlaceholder' })}
                className="!h-9 !w-[220px] !min-w-[190px]"
                value={keywordDraft}
                onChange={(event) => setKeywordDraft(event.target.value)}
                onPressEnter={handleSearch}
              />
              <RangePicker
                allowClear
                variant="filled"
                value={dateRange as any}
                format="YYYY-MM-DD"
                placeholder={[
                  intl.formatMessage({ id: 'pages.workflow.instance.startDate' }),
                  intl.formatMessage({ id: 'pages.workflow.instance.endDate' }),
                ]}
                className="!h-9 !w-[250px] !min-w-[230px]"
                onChange={(value) => {
                  setDateRange(value ? (value as unknown as Dayjs[]) : undefined);
                  setPage(1);
                  setSelectedKeys([]);
                }}
              />
              <YakButton className="!h-9 !px-4" onClick={handleSearch}>
                {intl.formatMessage({ id: 'pages.workflow.common.search' })}
              </YakButton>
              {hasActiveFilters ? (
                <YakButton type="text" className="!h-9 !px-2 !text-[#777c86]" onClick={resetAllFilters}>
                  {intl.formatMessage({ id: 'pages.workflow.common.reset' })}
                </YakButton>
              ) : null}
              <Popover
                placement="bottomRight"
                trigger="click"
                open={advancedOpen}
                onOpenChange={setAdvancedOpen}
                content={advancedFilters}
              >
                <YakButton
                  size="small"
                  icon={<FilterOutlined />}
                  className={[
                    '!h-9 !px-3',
                    advancedFilterCount > 0 ? '!border-[#ffccc7] !bg-[#fff1f0] !text-[#ff4d4f]' : '',
                  ].join(' ')}
                >
                  {intl.formatMessage({ id: 'pages.workflow.instance.advanced' })}
                  {advancedFilterCount > 0 ? (
                    <span className="ml-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-[#ff4d4f] px-1 text-[10px] leading-[18px] text-white">
                      {advancedFilterCount}
                    </span>
                  ) : null}
                </YakButton>
              </Popover>
            </div>
          </div>

          {selectedKeys.length > 0 ? (
            <div className="mt-2 flex min-h-10 shrink-0 items-center justify-between rounded-[10px] bg-[#fafbfc] px-3">
              <span className="text-[12px] text-[#667085]">
                {intl.formatMessage(
                  { id: 'pages.workflow.instance.selected' },
                  { count: selectedKeys.length },
                )}
              </span>
              <YakButton
                className="!h-8"
                icon={<RefreshCw size={13} />}
                loading={batchLoading}
                onClick={() => void batchRetry()}
              >
                {intl.formatMessage(
                  { id: 'pages.workflow.instance.batchRetry' },
                  { count: selectedKeys.length },
                )}
              </YakButton>
            </div>
          ) : null}

          <div className="mt-3 min-h-0 flex-1 overflow-auto">
            <Table<WorkflowInstance>
              rowKey="id"
              bordered
              size="small"
              pagination={false}
              loading={loading}
              dataSource={pageData}
              columns={columns}
              rowSelection={{
                selectedRowKeys: selectedKeys,
                onChange: setSelectedKeys,
                columnWidth: 48,
                getCheckboxProps: (record) => ({
                  disabled: !isRetryableInstance(record),
                  title: intl.formatMessage({
                    id: isRetryableInstance(record)
                      ? 'pages.workflow.instance.addBatchRecovery'
                      : 'pages.workflow.instance.noBatchRecovery',
                  }),
                }),
              }}
              scroll={{ x: 1320 }}
              locale={{
                emptyText: (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      <span className="text-[12px] text-[#98a2b3]">
                        {intl.formatMessage({ id: 'pages.workflow.instance.empty' })}
                      </span>
                    }
                  />
                ),
              }}
              className="compact-workflow-instance-table [&_.ant-table]:!text-[13px] [&_.ant-table-container]:!border-[#eaecf0] [&_.ant-table-cell]:!align-middle [&_.ant-table-thead>tr>th]:!h-10 [&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!px-4 [&_.ant-table-thead>tr>th]:!py-2 [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!font-medium [&_.ant-table-thead>tr>th]:!text-[#667085] [&_.ant-table-thead>tr>th]:!border-[#eaecf0] [&_.ant-table-tbody>tr>td]:!px-4 [&_.ant-table-tbody>tr>td]:!py-2.5 [&_.ant-table-tbody>tr>td]:!border-[#f0f2f5] [&_.ant-table-tbody>tr>td]:!text-[#667085] [&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc] [&_.ant-table-cell-fix-right]:!bg-white [&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc] [&_.ant-checkbox-inner]:!h-4 [&_.ant-checkbox-inner]:!w-4 [&_.ant-table-placeholder>td]:!h-[240px]"
            />
          </div>

          <div className="sticky bottom-0 z-20 mt-auto flex min-h-[56px] shrink-0 items-center justify-between border border-t-0 border-[#e5e7eb] bg-white px-5 py-3 shadow-[0_-4px_12px_rgba(16,24,40,0.04)]">
            <div className="text-[12px] text-[#98a2b3]">
              {intl.formatMessage(
                { id: 'pages.workflow.instance.total' },
                { count: filtered.length },
              )}
            </div>
            <div className="flex items-center gap-3">
              <Pagination
                size="small"
                total={filtered.length}
                current={page}
                pageSize={pageSize}
                showSizeChanger={false}
                onChange={setPage}
              />
              <Select
                size="small"
                value={pageSize}
                className="w-[92px]"
                onChange={(value) => {
                  setPageSize(value);
                  setPage(1);
                }}
                options={[10, 20, 50].map((value) => ({
                  value,
                  label: intl.formatMessage(
                    { id: 'pages.workflow.instance.perPage' },
                    { count: value },
                  ),
                }))}
              />
            </div>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default WorkflowInstancesPage;
