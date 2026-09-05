import YakButton from '@/components/YakButton';
import YakTab from '@/components/YakTab';
import { ReloadOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Progress,
  Space,
  Spin,
  Timeline,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { realtimeApi } from './api';
import type {
  RealtimeEvent,
  RealtimeJob,
  RealtimeObservability,
  RealtimeRuntimeLog,
} from './types';

const ACTIVE_STATES = new Set(['STARTING', 'RUNNING', 'STOPPING', 'UNKNOWN']);

const formatNumber = (value: number | undefined, locale: string) =>
  value === undefined || value === null
    ? '-'
    : new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(value);

const formatRate = (value: number | undefined, locale: string) =>
  value === undefined || value === null ? '-' : `${formatNumber(value, locale)}/s`;

const formatBytes = (value?: number) => {
  if (value === undefined || value === null) return '-';
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let current = value / 1024;
  let index = 0;
  while (current >= 1024 && index < units.length - 1) {
    current /= 1024;
    index += 1;
  }
  return `${current.toFixed(current >= 100 ? 0 : current >= 10 ? 1 : 2)} ${units[index]}`;
};

const formatByteRate = (value?: number) =>
  value === undefined || value === null ? '-' : `${formatBytes(value)}/s`;

const formatTime = (value: number | undefined, locale: string) =>
  value === undefined || value === null
    ? '-'
    : new Date(value).toLocaleString(locale);

const pressurePercent = (value?: number) =>
  value === undefined || value === null
    ? 0
    : Math.max(0, Math.min(100, Math.round(value / 10)));

function MetricCard({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <Card size="small" className="h-full">
      <div className="text-[12px] text-[#667085]">{label}</div>
      <div className="mt-1 text-[22px] font-semibold leading-8 text-[#101828]">
        {value}
      </div>
      {hint && <div className="mt-1 text-[11px] text-[#98a2b3]">{hint}</div>}
    </Card>
  );
}

function CodeBlock({ children, empty }: { children?: string; empty: string }) {
  return (
    <pre className="max-h-[520px] min-h-[180px] overflow-auto rounded-lg bg-[#101828] p-4 text-[12px] leading-5 text-[#d0d5dd]">
      {children || empty}
    </pre>
  );
}

interface Props {
  job: RealtimeJob;
  events: RealtimeEvent[];
}

export default function RealtimeRuntimeDetail({ job, events }: Props) {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const locale = intl.locale || 'zh-CN';
  const [observability, setObservability] = useState<RealtimeObservability>();
  const [observabilityLoading, setObservabilityLoading] = useState(false);
  const [submissionLog, setSubmissionLog] = useState('');
  const [runtimeLog, setRuntimeLog] = useState<RealtimeRuntimeLog>();
  const [submissionLoading, setSubmissionLoading] = useState(false);
  const [runtimeLoading, setRuntimeLoading] = useState(false);

  const formatDuration = (value?: number) => {
    if (value === undefined || value === null) return '-';
    const seconds = Math.max(0, Math.floor(value / 1000));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const rest = seconds % 60;
    if (days > 0) {
      return intl.formatMessage(
        { id: 'pages.realtimeSync.runtime.durationDaysHours' },
        { days, hours },
      );
    }
    if (hours > 0) {
      return intl.formatMessage(
        { id: 'pages.realtimeSync.runtime.durationHoursMinutes' },
        { hours, minutes },
      );
    }
    if (minutes > 0) {
      return intl.formatMessage(
        { id: 'pages.realtimeSync.runtime.durationMinutesSeconds' },
        { minutes, seconds: rest },
      );
    }
    return intl.formatMessage(
      { id: 'pages.realtimeSync.runtime.durationSeconds' },
      { seconds: rest },
    );
  };

  const engineJobId = job.latestDeployment?.engineJobId;
  const runtimeEnvironment = job.latestDeployment?.runtimeEnvironment;
  const hasDeployment = Boolean(job.latestDeployment);
  const canObserve = Boolean(engineJobId);

  const refreshObservability = useCallback(
    async (showError = true) => {
      if (!engineJobId) return;
      setObservabilityLoading(true);
      try {
        const result = await realtimeApi.observability(job.id);
        setObservability(result.data);
      } catch (error: any) {
        if (showError) {
          message.error(
            error?.message ||
              intlRef.current.formatMessage({
                id: 'pages.realtimeSync.runtime.observabilityUnavailable',
              }),
          );
        }
      } finally {
        setObservabilityLoading(false);
      }
    },
    [engineJobId, job.id],
  );

  useEffect(() => {
    setObservability(undefined);
    setSubmissionLog('');
    setRuntimeLog(undefined);
    if (engineJobId) void refreshObservability(false);
  }, [engineJobId, job.id, refreshObservability]);

  useEffect(() => {
    if (!engineJobId || !ACTIVE_STATES.has(job.observedState)) return undefined;
    const timer = window.setInterval(() => void refreshObservability(false), 5000);
    return () => window.clearInterval(timer);
  }, [engineJobId, job.observedState, refreshObservability]);

  const loadSubmissionLog = async () => {
    setSubmissionLoading(true);
    try {
      const result = await realtimeApi.submissionLog(job.id);
      setSubmissionLog(result.data.logs || '');
    } catch (error: any) {
      message.error(
        error?.message ||
          intl.formatMessage({
            id: 'pages.realtimeSync.runtime.submissionLogUnavailable',
          }),
      );
    } finally {
      setSubmissionLoading(false);
    }
  };

  const loadRuntimeLog = async () => {
    setRuntimeLoading(true);
    try {
      const result = await realtimeApi.runtimeLog(job.id);
      setRuntimeLog(result.data);
    } catch (error: any) {
      message.error(
        error?.message ||
          intl.formatMessage({
            id: 'pages.realtimeSync.runtime.runtimeLogUnavailable',
          }),
      );
    } finally {
      setRuntimeLoading(false);
    }
  };

  const flinkWebUrl = useMemo(() => {
    if (observability?.flinkWebUrl) return observability.flinkWebUrl;
    const restUrl = runtimeEnvironment?.config.restUrl;
    if (!engineJobId || !restUrl) return undefined;
    return `${restUrl.replace(/\/+$/, '')}/#/job/${engineJobId}/overview`;
  }, [engineJobId, observability?.flinkWebUrl, runtimeEnvironment?.config.restUrl]);

  const checkpoint = observability?.checkpoints;
  const latestCheckpoint = checkpoint?.latestCompleted;
  const metrics = observability?.metrics;

  const overview = !canObserve ? (
    <Empty
      description={intl.formatMessage({
        id: 'pages.realtimeSync.runtime.noObservableJobId',
      })}
    />
  ) : (
    <Spin spinning={observabilityLoading && !observability}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div className="flex items-center justify-between gap-3">
          <div className="text-[12px] text-[#98a2b3]">
            {observability?.sampledAt
              ? intl.formatMessage(
                  { id: 'pages.realtimeSync.runtime.sampledAt' },
                  { time: formatTime(observability.sampledAt, locale) },
                )
              : intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.loadingFlink',
                })}
          </div>
          <Space>
            {flinkWebUrl && (
              <YakButton size="small" href={flinkWebUrl} target="_blank">
                {intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.openFlinkWeb',
                })}
              </YakButton>
            )}
            <YakButton
              size="small"
              icon={<ReloadOutlined />}
              loading={observabilityLoading}
              onClick={() => void refreshObservability()}
            >
              {intl.formatMessage({ id: 'pages.realtimeSync.runtime.refresh' })}
            </YakButton>
          </Space>
        </div>

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.flinkState' })}
            value={observability?.flinkState || job.observedState || '-'}
            hint={
              observability?.flinkJobName ||
              intl.formatMessage({ id: 'pages.realtimeSync.runtime.waitingJobName' })
            }
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.duration' })}
            value={formatDuration(observability?.durationMs)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.readRate' })}
            value={formatRate(metrics?.recordsReadPerSecond, locale)}
            hint={intl.formatMessage(
              { id: 'pages.realtimeSync.runtime.totalRecords' },
              { count: formatNumber(metrics?.recordsRead, locale) },
            )}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.writeRate' })}
            value={formatRate(metrics?.recordsWrittenPerSecond, locale)}
            hint={intl.formatMessage(
              { id: 'pages.realtimeSync.runtime.totalRecords' },
              { count: formatNumber(metrics?.recordsWritten, locale) },
            )}
          />
        </div>

        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item
            label={intl.formatMessage({
              id: 'pages.realtimeSync.runtime.definitionVersion',
            })}
          >
            v{job.definitionVersion} /{' '}
            {intl.formatMessage(
              { id: 'pages.realtimeSync.runtime.publishedVersion' },
              { version: job.publishedVersion || '-' },
            )}
          </Descriptions.Item>
          <Descriptions.Item
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.runtimeIntent' })}
          >
            {job.desiredState} / {job.observedState}
          </Descriptions.Item>
          <Descriptions.Item
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.environment' })}
          >
            {runtimeEnvironment
              ? `${runtimeEnvironment.name} · env v${runtimeEnvironment.version}`
              : intl.formatMessage(
                  { id: 'pages.realtimeSync.runtime.environmentFallback' },
                  { id: job.runtimeEnvironmentId || '-' },
                )}
          </Descriptions.Item>
          <Descriptions.Item label="Flink REST">
            {runtimeEnvironment?.config.restUrl || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Flink JobId">{engineJobId || '-'}</Descriptions.Item>
          <Descriptions.Item label="Flink CDC Revision">
            {job.latestDeployment?.runtimeRevision || '-'}
          </Descriptions.Item>
          <Descriptions.Item
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.startTime' })}
          >
            {formatTime(observability?.startTime, locale)}
          </Descriptions.Item>
          <Descriptions.Item
            label={intl.formatMessage({
              id: 'pages.realtimeSync.runtime.latestCheckpoint',
            })}
          >
            {latestCheckpoint?.id
              ? `#${latestCheckpoint.id} · ${formatDuration(latestCheckpoint.durationMs)}`
              : '-'}
          </Descriptions.Item>
          <Descriptions.Item
            label={intl.formatMessage({
              id: 'pages.realtimeSync.runtime.deploymentSummary',
            })}
            span={2}
          >
            {job.latestDeployment?.specSummary || '-'}
          </Descriptions.Item>
          <Descriptions.Item
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.lastError' })}
            span={2}
          >
            {job.lastError || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Space>
    </Spin>
  );

  const logs = (
    <YakTab
      items={[
        {
          key: 'submission',
          label: intl.formatMessage({ id: 'pages.realtimeSync.runtime.submissionLog' }),
          children: hasDeployment ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Alert
                type="info"
                showIcon
                message={intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.submissionLogInfo',
                })}
              />
              <Button loading={submissionLoading} onClick={() => void loadSubmissionLog()}>
                {intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.readSubmissionLog',
                })}
              </Button>
              <CodeBlock
                empty={intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.submissionLogNotRead',
                })}
              >
                {submissionLog}
              </CodeBlock>
            </Space>
          ) : (
            <Empty
              description={intl.formatMessage({
                id: 'pages.realtimeSync.runtime.noDeploymentLog',
              })}
            />
          ),
        },
        {
          key: 'runtime',
          label: intl.formatMessage({
            id: 'pages.realtimeSync.runtime.runtimeDiagnostics',
          }),
          children: canObserve ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Alert
                type="info"
                showIcon
                message={intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.runtimeDiagnosticsInfo',
                })}
              />
              <YakButton loading={runtimeLoading} onClick={() => void loadRuntimeLog()}>
                {intl.formatMessage({
                  id: 'pages.realtimeSync.runtime.refreshDiagnostics',
                })}
              </YakButton>
              {runtimeLog?.rootException && (
                <Alert
                  type="error"
                  showIcon
                  message={intl.formatMessage(
                    { id: 'pages.realtimeSync.runtime.latestException' },
                    { time: formatTime(runtimeLog.timestamp, locale) },
                  )}
                  description={
                    <pre className="m-0 whitespace-pre-wrap text-[12px]">
                      {runtimeLog.rootException}
                    </pre>
                  }
                />
              )}
              {runtimeLog?.truncated && (
                <Alert
                  type="warning"
                  showIcon
                  message={intl.formatMessage({
                    id: 'pages.realtimeSync.runtime.truncated',
                  })}
                />
              )}
              {runtimeLog?.exceptions?.length ? (
                <Timeline
                  items={runtimeLog.exceptions.map((item, index) => ({
                    color: 'red',
                    children: (
                      <div key={`${item.timestamp || 0}-${index}`}>
                        <div className="font-medium text-[#344054]">
                          {item.exceptionName || 'Runtime Exception'}
                        </div>
                        <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                          {formatTime(item.timestamp, locale)} · {item.taskName || '-'} ·{' '}
                          {item.taskManagerId || '-'}
                        </div>
                        {item.stacktrace && (
                          <pre className="mt-2 max-h-[220px] overflow-auto whitespace-pre-wrap rounded bg-[#f8f9fb] p-3 text-[11px] text-[#475467]">
                            {item.stacktrace}
                          </pre>
                        )}
                      </div>
                    ),
                  }))}
                />
              ) : (
                <Empty
                  description={intl.formatMessage({
                    id: runtimeLog
                      ? 'pages.realtimeSync.runtime.noExceptionHistory'
                      : 'pages.realtimeSync.runtime.diagnosticsNotRead',
                  })}
                />
              )}
            </Space>
          ) : (
            <Empty
              description={intl.formatMessage({
                id: 'pages.realtimeSync.runtime.jobIdPending',
              })}
            />
          ),
        },
      ]}
    />
  );

  const checkpoints = !canObserve ? (
    <Empty
      description={intl.formatMessage({
        id: 'pages.realtimeSync.runtime.noCheckpoint',
      })}
    />
  ) : (
    <Spin spinning={observabilityLoading && !observability}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div className="flex items-center justify-between">
          <Typography.Text type="secondary">
            {intl.formatMessage({ id: 'pages.realtimeSync.runtime.checkpointSummary' })}
          </Typography.Text>
          <YakButton
            size="small"
            icon={<ReloadOutlined />}
            loading={observabilityLoading}
            onClick={() => void refreshObservability()}
          >
            {intl.formatMessage({ id: 'pages.realtimeSync.runtime.refresh' })}
          </YakButton>
        </div>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.success' })}
            value={formatNumber(checkpoint?.completed, locale)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.failed' })}
            value={formatNumber(checkpoint?.failed, locale)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.inProgress' })}
            value={formatNumber(checkpoint?.inProgress, locale)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.total' })}
            value={formatNumber(checkpoint?.total, locale)}
          />
        </div>
        {latestCheckpoint ? (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item
              label={intl.formatMessage({
                id: 'pages.realtimeSync.runtime.latestSuccessId',
              })}
            >
              #{latestCheckpoint.id}
            </Descriptions.Item>
            <Descriptions.Item
              label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.completedAt' })}
            >
              {formatTime(latestCheckpoint.latestAckTimestamp, locale)}
            </Descriptions.Item>
            <Descriptions.Item
              label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.elapsed' })}
            >
              {formatDuration(latestCheckpoint.durationMs)}
            </Descriptions.Item>
            <Descriptions.Item
              label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.stateSize' })}
            >
              {formatBytes(latestCheckpoint.stateSizeBytes)}
            </Descriptions.Item>
            <Descriptions.Item label="Checkpointed Size">
              {formatBytes(latestCheckpoint.checkpointedSizeBytes)}
            </Descriptions.Item>
            <Descriptions.Item
              label={intl.formatMessage({
                id: 'pages.realtimeSync.runtime.acknowledgedSubtasks',
              })}
            >
              {latestCheckpoint.acknowledgedSubtasks ?? '-'} /{' '}
              {latestCheckpoint.totalSubtasks ?? '-'}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Empty
            description={intl.formatMessage({
              id: 'pages.realtimeSync.runtime.noSuccessfulCheckpoint',
            })}
          />
        )}
        {checkpoint?.latestFailed?.failureMessage && (
          <Alert
            type="error"
            showIcon
            message={intl.formatMessage({
              id: 'pages.realtimeSync.runtime.latestCheckpointFailed',
            })}
            description={checkpoint.latestFailed.failureMessage}
          />
        )}
      </Space>
    </Spin>
  );

  const metricPanel = !canObserve ? (
    <Empty
      description={intl.formatMessage({ id: 'pages.realtimeSync.runtime.noMetrics' })}
    />
  ) : (
    <Spin spinning={observabilityLoading && !observability}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div className="flex items-center justify-between">
          <Typography.Text type="secondary">
            {intl.formatMessage({ id: 'pages.realtimeSync.runtime.metricsSummary' })}
          </Typography.Text>
          <YakButton
            size="small"
            icon={<ReloadOutlined />}
            loading={observabilityLoading}
            onClick={() => void refreshObservability()}
          >
            {intl.formatMessage({ id: 'pages.realtimeSync.runtime.refresh' })}
          </YakButton>
        </div>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.sourceRecords' })}
            value={formatNumber(metrics?.recordsRead, locale)}
            hint={formatRate(metrics?.recordsReadPerSecond, locale)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.sinkRecords' })}
            value={formatNumber(metrics?.recordsWritten, locale)}
            hint={formatRate(metrics?.recordsWrittenPerSecond, locale)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.sourceBytes' })}
            value={formatBytes(metrics?.bytesRead)}
            hint={formatByteRate(metrics?.bytesReadPerSecond)}
          />
          <MetricCard
            label={intl.formatMessage({ id: 'pages.realtimeSync.runtime.sinkBytes' })}
            value={formatBytes(metrics?.bytesWritten)}
            hint={formatByteRate(metrics?.bytesWrittenPerSecond)}
          />
        </div>
        <Card
          size="small"
          title={intl.formatMessage({ id: 'pages.realtimeSync.runtime.pressure' })}
        >
          <Space direction="vertical" size={14} style={{ width: '100%' }}>
            <div>
              <div className="mb-1 flex justify-between text-[12px] text-[#667085]">
                <span>{intl.formatMessage({ id: 'pages.realtimeSync.runtime.maxBusy' })}</span>
                <span>{metrics?.maxBusyMsPerSecond?.toFixed(0) ?? '-'} ms/s</span>
              </div>
              <Progress
                percent={pressurePercent(metrics?.maxBusyMsPerSecond)}
                showInfo={false}
              />
            </div>
            <div>
              <div className="mb-1 flex justify-between text-[12px] text-[#667085]">
                <span>
                  {intl.formatMessage({
                    id: 'pages.realtimeSync.runtime.maxBackpressure',
                  })}
                </span>
                <span>{metrics?.maxBackpressuredMsPerSecond?.toFixed(0) ?? '-'} ms/s</span>
              </div>
              <Progress
                percent={pressurePercent(metrics?.maxBackpressuredMsPerSecond)}
                showInfo={false}
                status={
                  pressurePercent(metrics?.maxBackpressuredMsPerSecond) >= 70
                    ? 'exception'
                    : 'normal'
                }
              />
            </div>
            <div>
              <div className="mb-1 flex justify-between text-[12px] text-[#667085]">
                <span>{intl.formatMessage({ id: 'pages.realtimeSync.runtime.maxIdle' })}</span>
                <span>{metrics?.maxIdleMsPerSecond?.toFixed(0) ?? '-'} ms/s</span>
              </div>
              <Progress
                percent={pressurePercent(metrics?.maxIdleMsPerSecond)}
                showInfo={false}
              />
            </div>
            <Typography.Text type="secondary" className="text-[11px]">
              {intl.formatMessage(
                { id: 'pages.realtimeSync.runtime.vertexCount' },
                { count: metrics?.vertexCount || 0 },
              )}
            </Typography.Text>
          </Space>
        </Card>
      </Space>
    </Spin>
  );

  return (
    <YakTab
      items={[
        {
          key: 'overview',
          label: intl.formatMessage({ id: 'pages.realtimeSync.runtime.overview' }),
          children: overview,
        },
        {
          key: 'events',
          label: intl.formatMessage({ id: 'pages.realtimeSync.runtime.events' }),
          children: events.length ? (
            <Timeline
              items={events.map((event) => ({
                color:
                  event.toState === 'FAILED' || event.toState === 'CONFLICT'
                    ? 'red'
                    : 'blue',
                children: (
                  <div>
                    <Typography.Text strong>{event.eventType}</Typography.Text>{' '}
                    <Typography.Text type="secondary">
                      {event.createTime}
                    </Typography.Text>
                    <div>
                      {event.fromState || '-'} → {event.toState || '-'} · {event.message}
                    </div>
                  </div>
                ),
              }))}
            />
          ) : (
            <Empty
              description={intl.formatMessage({ id: 'pages.realtimeSync.runtime.noEvents' })}
            />
          ),
        },
        {
          key: 'logs',
          label: intl.formatMessage({ id: 'pages.realtimeSync.runtime.logs' }),
          children: logs,
        },
        {
          key: 'checkpoints',
          label: intl.formatMessage({ id: 'pages.realtimeSync.runtime.checkpoints' }),
          children: checkpoints,
        },
        {
          key: 'metrics',
          label: intl.formatMessage({ id: 'pages.realtimeSync.runtime.metrics' }),
          children: metricPanel,
        },
      ]}
    />
  );
}
