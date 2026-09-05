import YakTab from '@/components/YakTab';
import {
  activateWorkflowInstance,
  cancelWorkflowInstance,
  getWorkflowInstance,
  getWorkflowInstanceOperations,
  isWorkflowTerminal,
  pauseWorkflowInstance,
  rerunWorkflowBusinessDate,
  rerunWorkflowFromNode,
  restartWorkflowInstance,
  resumeWorkflowInstance,
  retryWorkflowFailedNode,
  retryWorkflowFailedNodes,
  subscribeWorkflowEvents,
  type WorkflowAttempt,
  type WorkflowInstance,
  type WorkflowInstanceOperations,
  type WorkflowNodeInstance,
} from '@/services/workflow';
import { BRAND_THEME } from '@/styles/brand';
import { history, useIntl, useParams } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  ArrowLeft,
  CalendarDays,
  Pause,
  Play,
  RefreshCw,
  RotateCcw,
  Square,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type Key,
  type ReactNode,
} from 'react';
import WorkflowDagView from '../WorkflowDagView';

type DetailTabKey = 'overview' | 'dag' | 'nodes' | 'input';

const STATUS_MESSAGE_IDS: Record<string, string> = {
  CREATED: 'pages.workflow.instanceDetail.status.created',
  RUNNING: 'pages.workflow.instanceDetail.status.running',
  PAUSING: 'pages.workflow.instanceDetail.status.pausing',
  PAUSED: 'pages.workflow.instanceDetail.status.paused',
  RESUMING: 'pages.workflow.instanceDetail.status.resuming',
  SUCCESS: 'pages.workflow.instanceDetail.status.success',
  SUCCESS_WITH_WARNINGS: 'pages.workflow.instanceDetail.status.successWarnings',
  FAILED: 'pages.workflow.instanceDetail.status.failed',
  WARNING: 'pages.workflow.instanceDetail.status.warning',
  CANCELED: 'pages.workflow.instanceDetail.status.canceled',
  TIMED_OUT: 'pages.workflow.instanceDetail.status.timedOut',
  WAITING: 'pages.workflow.instanceDetail.status.waiting',
  READY: 'pages.workflow.instanceDetail.status.ready',
  SUBMITTED: 'pages.workflow.instanceDetail.status.submitted',
  UPSTREAM_FAILED: 'pages.workflow.instanceDetail.status.upstreamFailed',
  SKIPPED: 'pages.workflow.instanceDetail.status.skipped',
};

const FAILURE_REASON_MESSAGE_IDS: Record<string, string> = {
  EXECUTOR_FAILURE: 'pages.workflow.instanceDetail.failure.executor',
  DISPATCH_TIMEOUT: 'pages.workflow.instanceDetail.failure.dispatchTimeout',
  EXECUTION_TIMEOUT: 'pages.workflow.instanceDetail.failure.executionTimeout',
};

const statusClassName = (status: string) => {
  if (status === 'FAILED' || status === 'TIMED_OUT') return 'text-[#d92d20]';
  if (['RUNNING', 'RESUMING', 'SUBMITTED', 'READY'].includes(status)) {
    return 'font-medium text-[#344054]';
  }
  return 'text-[#667085]';
};

const statusDotClassName = (status: string) => {
  if (status === 'FAILED' || status === 'TIMED_OUT') return 'bg-[#f04438]';
  if (status === 'WARNING' || status === 'SUCCESS_WITH_WARNINGS') return 'bg-[#f79009]';
  if (status === 'RUNNING' || status === 'RESUMING' || status === 'SUCCESS') return 'bg-[#20c77a]';
  return 'bg-[#b0b5bd]';
};

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-';
const formatScheduleTime = (value?: string) => (value ? value.replace('T', ' ') : '-');

const JsonBlock = ({ value }: { value?: unknown }) => (
  <pre className="m-0 max-h-[420px] overflow-auto rounded-md bg-[#f7f7f8] p-3 text-[11px] leading-5 text-[rgba(22,24,35,.72)]">
    {JSON.stringify(value ?? {}, null, 2)}
  </pre>
);

const MetricTile = ({ label, value }: { label: string; value: ReactNode }) => (
  <div className="rounded-md bg-[#f7f7f8] px-4 py-4">
    <div className="text-[12px] leading-4 text-[#7c828c]">{label}</div>
    <div className="mt-2 truncate text-[20px] font-semibold leading-7 tracking-[-0.02em] text-[#161823]">
      {value}
    </div>
  </div>
);

const InfoField = ({ label, children, className = '' }: {
  label: string;
  children: ReactNode;
  className?: string;
}) => (
  <div className={className}>
    <div className="text-[12px] text-[#8a8f98]">{label}</div>
    <div className="mt-2 min-w-0 break-words text-[14px] font-medium text-[#161823]">
      {children}
    </div>
  </div>
);

const SectionCard = ({ title, extra, children, className = '' }: {
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  className?: string;
}) => (
  <section className={`min-w-0 rounded-lg bg-white ${className}`}>
    <div className="flex min-h-[52px] items-center justify-between gap-4 px-5">
      <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
      {extra ? <div className="text-[11px] text-[#98a2b3]">{extra}</div> : null}
    </div>
    {children}
  </section>
);

const WorkflowIllustration = () => (
  <div className="relative flex h-[116px] w-[116px] shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white">
    <svg width="80" height="80" viewBox="0 0 80 80" fill="none" aria-hidden="true" className="relative z-10 -translate-y-1" shapeRendering="crispEdges">
      <path d="M24 24H40V28H24V24Z" fill="#161823" />
      <path d="M40 24H56V28H40V24Z" fill="#161823" />
      <path d="M38 28H42V42H38V28Z" fill="#161823" />
      <path d="M20 28H24V38H20V28Z" fill="#161823" />
      <path d="M56 28H60V38H56V28Z" fill="#161823" />
      <path d="M20 38H34V42H20V38Z" fill="#161823" />
      <path d="M46 38H60V42H46V38Z" fill="#161823" />
      <rect x="12" y="14" width="20" height="14" rx="2" fill="#F3F4F6" stroke="#161823" strokeWidth="4" />
      <rect x="30" y="40" width="20" height="14" rx="2" fill="#F3F4F6" stroke="#161823" strokeWidth="4" />
      <rect x="48" y="14" width="20" height="14" rx="2" fill="#FFF1F3" stroke="#FE2C55" strokeWidth="4" />
      <rect x="20" y="18" width="4" height="4" fill="#161823" />
      <rect x="38" y="44" width="4" height="4" fill="#FE2C55" />
      <rect x="56" y="18" width="4" height="4" fill="#FE2C55" />
      <path d="M40 54H44V66H40V54Z" fill="#161823" />
      <path d="M34 64H50V68H34V64Z" fill="#161823" />
    </svg>
    <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20 h-[46px] bg-gradient-to-b from-transparent via-black/10 to-black/25" />
  </div>
);

export default function WorkflowInstanceDetailPage() {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const params = useParams<{ executionId?: string }>();
  const executionId = params.executionId;
  const streamRef = useRef<(() => void) | null>(null);
  const [detail, setDetail] = useState<WorkflowInstance>();
  const [operations, setOperations] = useState<WorkflowInstanceOperations>();
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string>();
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [expandedNodeIds, setExpandedNodeIds] = useState<Key[]>([]);
  const [activeTab, setActiveTab] = useState<DetailTabKey>('overview');
  const [rerunOpen, setRerunOpen] = useState(false);
  const [rerunDate, setRerunDate] = useState(dayjs());
  const [rerunStrategy, setRerunStrategy] = useState<'SERIAL_WAIT' | 'PARALLEL'>('SERIAL_WAIT');
  const [rerunInput, setRerunInput] = useState('{}');

  const statusText = (status: string) => {
    const id = STATUS_MESSAGE_IDS[status];
    return id ? intl.formatMessage({ id }) : status;
  };
  const failureReasonText = (reason?: string) => {
    if (!reason) return '-';
    const id = FAILURE_REASON_MESSAGE_IDS[reason];
    return id ? intl.formatMessage({ id }) : reason;
  };
  const formatDuration = (record?: WorkflowInstance) => {
    if (!record?.startedAt) return '-';
    const seconds = Math.max(
      0,
      dayjs(record.endedAt || undefined).diff(dayjs(record.startedAt), 'second'),
    );
    if (seconds < 60) {
      return intl.formatMessage(
        { id: 'pages.workflow.instanceDetail.durationSeconds' },
        { seconds },
      );
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return intl.formatMessage(
        { id: 'pages.workflow.instanceDetail.durationMinutesSeconds' },
        { minutes, seconds: seconds % 60 },
      );
    }
    return intl.formatMessage(
      { id: 'pages.workflow.instanceDetail.durationHoursMinutes' },
      { hours: Math.floor(minutes / 60), minutes: minutes % 60 },
    );
  };

  const applySnapshot = useCallback((snapshot: WorkflowInstance) => {
    setDetail(snapshot);
  }, []);

  const attachStream = useCallback((snapshot: WorkflowInstance) => {
    streamRef.current?.();
    streamRef.current = null;
    if (!isWorkflowTerminal(snapshot.status)) {
      streamRef.current = subscribeWorkflowEvents(snapshot.id, applySnapshot);
    }
  }, [applySnapshot]);

  const load = useCallback(async () => {
    if (!executionId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [instance, ops] = await Promise.all([
        getWorkflowInstance(executionId),
        getWorkflowInstanceOperations(executionId),
      ]);
      applySnapshot(instance);
      setOperations(ops);
      setRerunDate(dayjs(ops.businessDate || undefined));
      setSelectedNodeId(undefined);
      setExpandedNodeIds([]);
      attachStream(instance);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.loadFailed' }),
      );
      setDetail(undefined);
    } finally {
      setLoading(false);
    }
  }, [applySnapshot, attachStream, executionId]);

  useEffect(() => {
    void load();
    return () => {
      streamRef.current?.();
      streamRef.current = null;
    };
  }, [load]);

  const runAction = useCallback(async (
    key: string,
    operation: () => Promise<WorkflowInstance>,
    success: string,
  ) => {
    if (actionLoading) return;
    setActionLoading(key);
    try {
      const snapshot = await operation();
      applySnapshot(snapshot);
      attachStream(snapshot);
      message.success(success);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.operationFailed' }),
      );
    } finally {
      setActionLoading(undefined);
    }
  }, [actionLoading, applySnapshot, attachStream]);

  const activatePrepared = useCallback(async (prepared: WorkflowInstance, success: string) => {
    const activated = await activateWorkflowInstance(prepared.id);
    message.success(`${success}: ${activated.id}`);
    history.push(`/workflow/instances/${encodeURIComponent(activated.id)}`);
  }, []);

  const handleRestart = async () => {
    if (!detail || actionLoading) return;
    setActionLoading('restart');
    try {
      await activatePrepared(
        await restartWorkflowInstance(detail.id),
        intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.restartPrepared' }),
      );
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.restartFailed' }),
      );
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleRerunFromNode = async (nodeId: string) => {
    if (!detail || actionLoading) return;
    setActionLoading(`rerun:${nodeId}`);
    try {
      await activatePrepared(
        await rerunWorkflowFromNode(detail.id, nodeId),
        intlRef.current.formatMessage(
          { id: 'pages.workflow.instanceDetail.rerunFromNodePrepared' },
          { nodeId },
        ),
      );
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.rerunFromNodeFailed' }),
      );
    } finally {
      setActionLoading(undefined);
    }
  };

  const handleRetryNode = async (nodeId: string) => {
    if (!detail) return;
    await runAction(
      `retryNode:${nodeId}`,
      () => retryWorkflowFailedNode(detail.id, nodeId),
      intlRef.current.formatMessage(
        { id: 'pages.workflow.instanceDetail.retryNodeSuccess' },
        { nodeId },
      ),
    );
  };

  const handleBusinessDateRerun = async () => {
    if (!detail || !rerunDate || actionLoading) return;
    let input: Record<string, unknown> = {};
    try {
      input = rerunInput.trim() ? JSON.parse(rerunInput) : {};
    } catch {
      message.error(intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunJsonInvalid' }));
      return;
    }
    setActionLoading('businessDate');
    try {
      const batch = await rerunWorkflowBusinessDate(detail.id, {
        businessDate: rerunDate.format('YYYY-MM-DD'),
        executionStrategy: rerunStrategy,
        input,
      });
      message.success(
        intlRef.current.formatMessage(
          { id: 'pages.workflow.instanceDetail.rerunCreated' },
          { count: batch.totalCount },
        ),
      );
      setRerunOpen(false);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.rerunFailed' }),
      );
    } finally {
      setActionLoading(undefined);
    }
  };

  const selectDagNode = (nodeId: string) => {
    setSelectedNodeId(nodeId);
    setExpandedNodeIds((current) =>
      current.includes(nodeId) ? current : [...current, nodeId],
    );
    setActiveTab('nodes');
  };

  const attemptColumns = useMemo<ColumnsType<WorkflowAttempt>>(
    () => [
      { title: '#', dataIndex: 'attemptNumber', width: 48 },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.attempt.status' }),
        dataIndex: 'status',
        width: 105,
        render: (value: string) => statusText(value),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.attempt.failureReason' }),
        dataIndex: 'failureReason',
        width: 120,
        render: (value?: string) => failureReasonText(value),
      },
      {
        title: 'Attempt ID',
        dataIndex: 'id',
        render: (value: string) => (
          <span className="font-mono text-[10px] text-[#667085]">{value}</span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.attempt.startedAt' }),
        dataIndex: 'startedAt',
        width: 155,
        render: formatTime,
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.attempt.endedAt' }),
        dataIndex: 'endedAt',
        width: 155,
        render: formatTime,
      },
    ],
    [intl],
  );

  const renderNodeDetail = (record: WorkflowNodeInstance) => (
    <div className="space-y-4 bg-[#fafafa] p-3">
      {record.errorMessage ? (
        <div className="rounded-md border border-[#fecdca] bg-[#fff6f5] px-3 py-2 text-[11px] text-[#b42318]">
          {record.errorMessage}
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-x-6 gap-y-2 text-[11px] text-[#667085] sm:grid-cols-2">
        <div>
          {intl.formatMessage({ id: 'pages.workflow.instanceDetail.currentAttempt' })}
          <span className="font-mono">{record.currentAttemptId || '-'}</span>
        </div>
        <div>
          {intl.formatMessage(
            { id: 'pages.workflow.instanceDetail.retrySummary' },
            { max: record.retryMaxAttempts, delay: record.retryDelaySeconds },
          )}
        </div>
        <div>
          {intl.formatMessage(
            { id: 'pages.workflow.instanceDetail.dispatchTimeout' },
            { value: record.dispatchTimeoutSeconds > 0 ? `${record.dispatchTimeoutSeconds}s` : '-' },
          )}
        </div>
        <div>
          {intl.formatMessage(
            { id: 'pages.workflow.instanceDetail.executionTimeout' },
            { value: record.executionTimeoutSeconds > 0 ? `${record.executionTimeoutSeconds}s` : '-' },
          )}
        </div>
      </div>
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Input Mapping</div><JsonBlock value={record.inputMapping} /></div>
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Resolved Node Input</div><JsonBlock value={record.input} /></div>
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Predecessor Outputs</div><JsonBlock value={record.predecessorOutputs} /></div>
        <div><div className="mb-1.5 text-[11px] font-medium text-[#344054]">Node Output</div><JsonBlock value={record.output} /></div>
      </div>
      <div>
        <div className="mb-1.5 text-[11px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.workflow.instanceDetail.attemptHistory' })}
        </div>
        <Table<WorkflowAttempt>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={record.attempts}
          columns={attemptColumns}
          scroll={{ x: 780 }}
        />
      </div>
    </div>
  );

  const nodeColumns = useMemo<ColumnsType<WorkflowNodeInstance>>(
    () => [
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.node' }),
        dataIndex: 'name',
        minWidth: 190,
        render: (_: unknown, record) => (
          <div>
            <div className="font-medium text-[#344054]">{record.name}</div>
            <div className="mt-0.5 text-[11px] text-[#98a2b3]">{record.type} · {record.id}</div>
          </div>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.nodeStatus' }),
        dataIndex: 'status',
        width: 115,
        render: (value: string) => (
          <span className={statusClassName(value)}>{statusText(value)}</span>
        ),
      },
      {
        title: 'Attempt',
        width: 95,
        render: (_: unknown, record) => (
          <span className="text-[12px] text-[#667085]">
            {record.currentAttemptNumber || 0} / {record.retryMaxAttempts}
          </span>
        ),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.failureReason' }),
        dataIndex: 'failureReason',
        width: 125,
        render: (value?: string, record?: WorkflowNodeInstance) =>
          record?.status === 'UPSTREAM_FAILED'
            ? intl.formatMessage({ id: 'pages.workflow.instanceDetail.notExecuted' })
            : failureReasonText(value),
      },
      {
        title: intl.formatMessage({ id: 'pages.workflow.instanceDetail.nodeOperations' }),
        width: 220,
        fixed: 'right',
        render: (_: unknown, record) => {
          if (!detail || !isWorkflowTerminal(detail.status)) return '-';
          return (
            <div className="flex items-center gap-1">
              {record.status === 'FAILED' ? (
                <Button
                  type="link"
                  size="small"
                  className="!px-1 !text-[12px]"
                  loading={actionLoading === `retryNode:${record.id}`}
                  onClick={() => void handleRetryNode(record.id)}
                >
                  {intl.formatMessage({ id: 'pages.workflow.instanceDetail.retryFailedNodes' })}
                </Button>
              ) : null}
              <Button
                type="link"
                size="small"
                className="!px-1 !text-[12px]"
                loading={actionLoading === `rerun:${record.id}`}
                onClick={() => void handleRerunFromNode(record.id)}
              >
                {intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunFromNode' })}
              </Button>
            </div>
          );
        },
      },
    ],
    [actionLoading, detail, intl],
  );

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Spin size="large" />
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f7f8]">
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={intl.formatMessage({ id: 'pages.workflow.instanceDetail.notFound' })}
        >
          <Button onClick={() => history.push('/workflow/instances')}>
            {intl.formatMessage({ id: 'pages.workflow.instanceDetail.back' })}
          </Button>
        </Empty>
      </div>
    );
  }

  const canRetryFailed = Boolean(
    isWorkflowTerminal(detail.status) &&
      detail.status !== 'SUCCESS' &&
      detail.nodes.some((node) =>
        ['FAILED', 'UPSTREAM_FAILED', 'CANCELED', 'SKIPPED'].includes(node.status),
      ),
  );
  const sourceExecutionId = String(detail.input?.sourceExecutionId || detail.sourceExecutionId || '');
  const successfulNodes = detail.nodes.filter((node) => node.status === 'SUCCESS').length;
  const activeNodes = detail.nodes.filter((node) =>
    ['RUNNING', 'READY', 'SUBMITTED', 'WAITING'].includes(node.status),
  ).length;
  const failedNodes = detail.nodes.filter((node) =>
    ['FAILED', 'UPSTREAM_FAILED', 'TIMED_OUT'].includes(node.status),
  ).length;
  const totalAttempts = detail.nodes.reduce(
    (sum, node) => sum + (node.attemptCount || node.attempts?.length || 0),
    0,
  );
  const triggerType = operations?.triggerType || (detail.testRun ? 'TEST' : 'MANUAL');

  const overviewContent = (
    <div className="grid gap-3 xl:grid-cols-2">
      <SectionCard title={intl.formatMessage({ id: 'pages.workflow.instanceDetail.runOverview' })}>
        <div className="grid grid-cols-2 gap-3 p-5 md:grid-cols-3">
          <MetricTile label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.nodeCount' })} value={detail.nodeCount || detail.nodes.length} />
          <MetricTile label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.successNodes' })} value={successfulNodes} />
          <MetricTile label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.runningNodes' })} value={activeNodes} />
          <MetricTile label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.failedNodes' })} value={failedNodes} />
          <MetricTile label="Attempts" value={totalAttempts} />
          <MetricTile label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.duration' })} value={formatDuration(detail)} />
        </div>
      </SectionCard>

      <SectionCard title={intl.formatMessage({ id: 'pages.workflow.instanceDetail.instanceInfo' })}>
        <div className="grid grid-cols-1 gap-x-10 gap-y-6 p-5 sm:grid-cols-2">
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.instanceId' })} className="sm:col-span-2">
            <span className="font-mono text-[12px] font-normal text-[#475467]">{detail.id}</span>
          </InfoField>
          <InfoField label="Definition ID">
            <span className="font-mono text-[12px] font-normal text-[#475467]">{detail.definitionId || '-'}</span>
          </InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.version' })}>
            {detail.workflowVersionNo ? `V${detail.workflowVersionNo}` : '-'}
          </InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.triggerSource' })}>{triggerType}</InfoField>
          <InfoField label="businessDate">{operations?.businessDate || String(detail.input?.businessDate || '-')}</InfoField>
          <InfoField label="scheduleTime">{formatScheduleTime(operations?.scheduleTime)}</InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.timezone' })}>{operations?.scheduleTimezone || '-'}</InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.startedAt' })}>{formatTime(detail.startedAt)}</InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.endedAt' })}>{formatTime(detail.endedAt)}</InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.failureStrategy' })}>{detail.failureStrategy || '-'}</InfoField>
          <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.workflowTimeout' })}>
            {detail.workflowTimeoutSeconds > 0 ? `${detail.workflowTimeoutSeconds}s` : '-'}
          </InfoField>
          {sourceExecutionId ? (
            <InfoField label={intl.formatMessage({ id: 'pages.workflow.instanceDetail.sourceInstance' })} className="sm:col-span-2">
              <span className="font-mono text-[12px] font-normal text-[#475467]">{sourceExecutionId}</span>
            </InfoField>
          ) : null}
        </div>
      </SectionCard>
    </div>
  );

  const dagContent = (
    <SectionCard
      title={intl.formatMessage({ id: 'pages.workflow.instanceDetail.dagTitle' })}
      extra={intl.formatMessage({ id: 'pages.workflow.instanceDetail.dagHint' })}
    >
      <div className="p-5 pt-1">
        <WorkflowDagView
          instance={detail}
          operations={operations}
          selectedNodeId={selectedNodeId}
          onSelectNode={selectDagNode}
        />
      </div>
    </SectionCard>
  );

  const nodesContent = (
    <SectionCard title={intl.formatMessage({ id: 'pages.workflow.instanceDetail.nodeDetails' })}>
      <div className="p-5">
        <Table<WorkflowNodeInstance>
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={detail.nodes}
          columns={nodeColumns}
          rowClassName={(record) => (record.id === selectedNodeId ? '!bg-[#f8f9fb]' : '')}
          expandable={{
            expandedRowRender: renderNodeDetail,
            rowExpandable: () => true,
            expandedRowKeys: expandedNodeIds,
            onExpandedRowsChange: (keys) => setExpandedNodeIds([...keys]),
          }}
          scroll={{ x: 820 }}
          className="[&_.ant-table-container]:!rounded-md [&_.ant-table-container]:!border [&_.ant-table-container]:!border-solid [&_.ant-table-container]:!border-[#eceef1] [&_.ant-table-thead>tr>th]:!bg-[#f7f7f8] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-tbody>tr>td]:!py-3 [&_.ant-table-tbody>tr>td]:!text-[12px]"
        />
      </div>
    </SectionCard>
  );

  const inputContent = (
    <SectionCard title="Workflow Input">
      <div className="p-5 pt-1"><JsonBlock value={detail.input} /></div>
    </SectionCard>
  );

  const tabItems: Array<{ key: DetailTabKey; label: string; children: ReactNode }> = [
    { key: 'overview', label: intl.formatMessage({ id: 'pages.workflow.instanceDetail.overview' }), children: overviewContent },
    { key: 'dag', label: intl.formatMessage({ id: 'pages.workflow.instanceDetail.dagTab' }), children: dagContent },
    { key: 'nodes', label: intl.formatMessage({ id: 'pages.workflow.instanceDetail.nodesTab' }), children: nodesContent },
    { key: 'input', label: 'Workflow Input', children: inputContent },
  ];

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f7f8] text-[#161823]">
        <div className="mx-auto w-full max-w-[1800px] px-4 pb-8 pt-0 lg:px-5">
          <div className="mb-2 flex h-10 items-center">
            <Button
              type="text"
              icon={<ArrowLeft size={15} />}
              className="!h-9 !px-1 !text-[14px] !font-semibold !text-[#30343b]"
              onClick={() => history.push('/workflow/instances')}
            >
              {intl.formatMessage({ id: 'pages.workflow.instanceDetail.back' })}
            </Button>
          </div>

          <section className="rounded-lg bg-white">
            <div className="grid min-h-[176px] gap-6 px-5 py-6 lg:px-6 xl:grid-cols-[116px_minmax(0,1fr)_minmax(280px,auto)] xl:items-center">
              <WorkflowIllustration />
              <div className="min-w-0">
                <div className="max-w-[680px] truncate text-[14px] font-medium leading-5 text-[#161823]">
                  {detail.name || intl.formatMessage({ id: 'pages.workflow.instanceDetail.unnamed' })}
                </div>
                <div className="mt-1 text-[12px] leading-4 text-[#8a8f98]">{formatTime(detail.startedAt)}</div>
                <div className="mt-1 flex items-center gap-1 text-[11px] leading-4 text-[#667085]">
                  <span className={`inline-block h-[10px] w-[10px] rounded-full ${statusDotClassName(detail.status)}`} />
                  <span>{statusText(detail.status)}</span>
                </div>
                <div className="mt-2 flex min-w-0 items-center gap-2 text-[11px] leading-4 text-[#8a8f98]">
                  <span>{intl.formatMessage({ id: 'pages.workflow.instanceDetail.instance' })}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span className="truncate font-mono">{detail.id}</span>
                </div>
                <div className="mt-1.5 flex min-w-0 items-center gap-1.5 text-[11px] leading-4 text-[#8a8f98]">
                  <span className="truncate">{detail.definitionId || 'Workflow'}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span>{detail.workflowVersionNo ? `V${detail.workflowVersionNo}` : 'V-'}</span>
                  <span className="text-[#d0d5dd]">·</span>
                  <span>{triggerType}</span>
                  {operations?.businessDate ? (
                    <>
                      <span className="text-[#d0d5dd]">·</span>
                      <span>{operations.businessDate}</span>
                    </>
                  ) : null}
                </div>
              </div>

              <div className="flex min-w-0 flex-wrap items-center gap-2 xl:justify-end">
                {detail.status === 'RUNNING' ? (
                  <Button
                    icon={<Pause size={13} />}
                    loading={actionLoading === 'pause'}
                    onClick={() =>
                      void runAction(
                        'pause',
                        () => pauseWorkflowInstance(detail.id),
                        intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.pauseSuccess' }),
                      )
                    }
                  >
                    {intl.formatMessage({ id: 'pages.workflow.instanceDetail.pause' })}
                  </Button>
                ) : null}
                {detail.status === 'PAUSED' ? (
                  <Button
                    icon={<Play size={13} />}
                    loading={actionLoading === 'resume'}
                    onClick={() =>
                      void runAction(
                        'resume',
                        () => resumeWorkflowInstance(detail.id),
                        intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.resumeSuccess' }),
                      )
                    }
                  >
                    {intl.formatMessage({ id: 'pages.workflow.instanceDetail.resume' })}
                  </Button>
                ) : null}
                {!isWorkflowTerminal(detail.status) ? (
                  <Popconfirm
                    title={intl.formatMessage({ id: 'pages.workflow.instanceDetail.cancelTitle' })}
                    onConfirm={() =>
                      void runAction(
                        'cancel',
                        () => cancelWorkflowInstance(detail.id),
                        intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.cancelSuccess' }),
                      )
                    }
                  >
                    <Button danger icon={<Square size={12} />} loading={actionLoading === 'cancel'}>
                      {intl.formatMessage({ id: 'pages.workflow.instanceDetail.cancel' })}
                    </Button>
                  </Popconfirm>
                ) : null}
                {canRetryFailed ? (
                  <Button
                    icon={<RefreshCw size={12} />}
                    loading={actionLoading === 'retryFailed'}
                    onClick={() =>
                      void runAction(
                        'retryFailed',
                        () => retryWorkflowFailedNodes(detail.id),
                        intlRef.current.formatMessage({ id: 'pages.workflow.instanceDetail.retryFailedSuccess' }),
                      )
                    }
                  >
                    {intl.formatMessage({ id: 'pages.workflow.instanceDetail.retryFailedNodes' })}
                  </Button>
                ) : null}
                {operations?.businessDateRerunSupported ? (
                  <Button icon={<CalendarDays size={12} />} onClick={() => setRerunOpen(true)}>
                    {intl.formatMessage({ id: 'pages.workflow.instanceDetail.businessDateRerun' })}
                  </Button>
                ) : operations ? (
                  <Tooltip title={operations.businessDateRerunUnavailableReason}>
                    <span>
                      <Button disabled icon={<CalendarDays size={12} />}>
                        {intl.formatMessage({ id: 'pages.workflow.instanceDetail.businessDateRerun' })}
                      </Button>
                    </span>
                  </Tooltip>
                ) : null}
                {isWorkflowTerminal(detail.status) ? (
                  <Button
                    type="primary"
                    icon={<RotateCcw size={12} />}
                    loading={actionLoading === 'restart'}
                    onClick={() => void handleRestart()}
                  >
                    {intl.formatMessage({ id: 'pages.workflow.instanceDetail.restart' })}
                  </Button>
                ) : null}
              </div>
            </div>
          </section>

          <div className="px-5 lg:px-6">
            <YakTab
              activeKey={activeTab}
              onChange={(key) => setActiveTab(key as DetailTabKey)}
              items={tabItems.map(({ key, label }) => ({ key, label }))}
            />
          </div>
          <div className="mt-3">{tabItems.find((item) => item.key === activeTab)?.children}</div>
        </div>
      </div>

      <Modal
        open={rerunOpen}
        title={intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.title' })}
        okText={intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.create' })}
        cancelText={intl.formatMessage({ id: 'pages.workflow.common.cancel' })}
        confirmLoading={actionLoading === 'businessDate'}
        onCancel={() => setRerunOpen(false)}
        onOk={() => void handleBusinessDateRerun()}
      >
        <div className="mb-4 rounded-md bg-[#f8f9fb] px-3 py-2 text-[11px] leading-5 text-[#667085]">
          {intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.description' })}
        </div>
        <div className="space-y-4">
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">businessDate</div>
            <DatePicker className="w-full" value={rerunDate} onChange={(value) => value && setRerunDate(value)} />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">
              {intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.strategy' })}
            </div>
            <Select
              className="w-full"
              value={rerunStrategy}
              onChange={setRerunStrategy}
              options={[
                { value: 'SERIAL_WAIT', label: intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.serial' }) },
                { value: 'PARALLEL', label: intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.parallel' }) },
              ]}
            />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] text-[#667085]">
              {intl.formatMessage({ id: 'pages.workflow.instanceDetail.rerunModal.input' })}
            </div>
            <Input.TextArea
              rows={6}
              spellCheck={false}
              className="font-mono text-[12px]"
              value={rerunInput}
              onChange={(event) => setRerunInput(event.target.value)}
            />
          </div>
        </div>
      </Modal>
    </ConfigProvider>
  );
}
