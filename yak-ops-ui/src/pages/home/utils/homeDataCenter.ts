import type { HomeDataCenterOverview } from '@/services/home';

import type {
  HomeDataCenterPeriodKey,
  HomeOverviewMetric,
} from '../types';

const pad2 = (value: number) => String(value).padStart(2, '0');

export const formatDate = (date: Date) =>
  `${date.getFullYear()}.${pad2(date.getMonth() + 1)}.${pad2(date.getDate())}`;

export const formatIsoDate = (value?: string) => {
  if (!value) return '-';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value.replaceAll('-', '.')
    : formatDate(date);
};

export function buildPeriod(
  periodKey: HomeDataCenterPeriodKey,
  reference = new Date(),
) {
  const today = new Date(
    reference.getFullYear(),
    reference.getMonth(),
    reference.getDate(),
  );
  const end = new Date(today);
  end.setDate(today.getDate() - 1);
  const count =
    periodKey === '30d' ? 30 : periodKey === 'yesterday' ? 1 : 7;
  const start = new Date(end);
  start.setDate(end.getDate() - (count - 1));
  return { start, end };
}

export const formatCount = (value: number, locale = 'zh-CN') =>
  new Intl.NumberFormat(locale, {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);

export const formatDuration = (millis?: number) => {
  const seconds = Math.max(0, Math.round((millis || 0) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
};

export const formatCardDuration = (millis?: number) => {
  const totalSeconds = Math.max(0, Math.round((millis || 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  return `${pad2(minutes)}:${pad2(totalSeconds % 60)}`;
};

const signedNumber = (value: number) => `${value > 0 ? '+' : ''}${value}`;
const signedDuration = (value: number) =>
  `${value > 0 ? '+' : value < 0 ? '-' : ''}${formatDuration(Math.abs(value))}`;
const signedRate = (value: number) =>
  `${value > 0 ? '+' : ''}${Number(value || 0).toFixed(1)}%`;

export interface HomeOverviewMetricLabels {
  successTasks: string;
  running: string;
  failedTasks: string;
  scheduleCount: string;
  processedRecords: string;
  avgDuration: string;
  compare1d: string;
  compare7d: string;
  compare30d: string;
}

const compareLabelFor = (
  periodKey: HomeDataCenterPeriodKey,
  labels: HomeOverviewMetricLabels,
) =>
  periodKey === 'yesterday'
    ? labels.compare1d
    : periodKey === '30d'
      ? labels.compare30d
      : labels.compare7d;

const positiveWhenUp = (value: number): HomeOverviewMetric['tone'] =>
  value > 0 ? 'positive' : value < 0 ? 'negative' : 'neutral';

const positiveWhenDown = (value: number): HomeOverviewMetric['tone'] =>
  value < 0 ? 'positive' : value > 0 ? 'negative' : 'neutral';

export const toOverviewMetrics = (
  overview: HomeDataCenterOverview | undefined,
  periodKey: HomeDataCenterPeriodKey,
  labels: HomeOverviewMetricLabels,
  locale = 'zh-CN',
): HomeOverviewMetric[] => {
  const metrics = overview?.metrics;
  const compare = overview?.compare;
  const compareLabel = compareLabelFor(periodKey, labels);
  return [
    {
      label: labels.successTasks,
      value: String(metrics?.successCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.successCount ?? 0),
      tone: positiveWhenUp(compare?.successCount ?? 0),
    },
    {
      label: labels.running,
      value: String(metrics?.runningCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.runningCount ?? 0),
      tone: positiveWhenDown(compare?.runningCount ?? 0),
    },
    {
      label: labels.failedTasks,
      value: String(metrics?.failedCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.failedCount ?? 0),
      tone: positiveWhenDown(compare?.failedCount ?? 0),
    },
    {
      label: labels.scheduleCount,
      value: String(metrics?.scheduleCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.scheduleCount ?? 0),
      tone: 'neutral',
    },
    {
      label: labels.processedRecords,
      value: formatCount(metrics?.processedRecords ?? 0, locale),
      compareLabel,
      compareValue: signedRate(compare?.processedRecordsRate ?? 0),
      tone: positiveWhenUp(compare?.processedRecordsRate ?? 0),
    },
    {
      label: labels.avgDuration,
      value: formatDuration(metrics?.avgDurationMs ?? 0),
      compareLabel,
      compareValue: signedDuration(compare?.avgDurationMs ?? 0),
      tone: positiveWhenDown(compare?.avgDurationMs ?? 0),
    },
  ];
};

export type HomeTaskTypeKey = 'offline' | 'workflow' | 'dataQuality' | 'task';

export const taskTypeKey = (taskType?: string): HomeTaskTypeKey => {
  if (taskType === 'OFFLINE_SYNC') return 'offline';
  if (taskType === 'WORKFLOW') return 'workflow';
  if (taskType === 'DATA_QUALITY') return 'dataQuality';
  return 'task';
};

const DEFAULT_TASK_TYPE_LABELS: Record<HomeTaskTypeKey, string> = {
  offline: '离线同步',
  workflow: '工作流',
  dataQuality: '数据质量',
  task: '任务',
};

export const taskTypeLabel = (
  taskType?: string,
  resolve?: (key: HomeTaskTypeKey) => string,
) => {
  const key = taskTypeKey(taskType);
  return resolve?.(key) ?? DEFAULT_TASK_TYPE_LABELS[key];
};

export type HomeStatusKey = 'success' | 'failed' | 'running' | 'cancelled';

export const statusKey = (status?: string): HomeStatusKey | undefined => {
  const normalized = status?.toUpperCase();
  if (
    [
      'SUCCEEDED',
      'SUCCESS',
      'SUCCESS_WITH_WARNINGS',
      'COMPLETED',
      'FINISHED',
      'WARNING',
    ].includes(normalized || '')
  ) {
    return 'success';
  }
  if (['FAILED', 'ERROR', 'TIMED_OUT', 'LOST'].includes(normalized || '')) {
    return 'failed';
  }
  if (
    [
      'CREATED',
      'SUBMITTED',
      'QUEUED',
      'RUNNING',
      'PAUSING',
      'PAUSED',
      'RESUMING',
    ].includes(normalized || '')
  ) {
    return 'running';
  }
  if (['CANCELED', 'CANCELLED'].includes(normalized || '')) return 'cancelled';
  return undefined;
};

const DEFAULT_STATUS_LABELS: Record<HomeStatusKey, string> = {
  success: '成功',
  failed: '失败',
  running: '运行中',
  cancelled: '已取消',
};

export const statusLabel = (
  status?: string,
  resolve?: (key: HomeStatusKey) => string,
) => {
  const key = statusKey(status);
  return key ? resolve?.(key) ?? DEFAULT_STATUS_LABELS[key] : status || '-';
};

export const statusClassName = (status?: string) => {
  const key = statusKey(status);
  if (key === 'success') return 'text-[#20a464]';
  if (key === 'failed') return 'text-[#f04c5a]';
  return 'text-[#7b8089]';
};

export const formatRunTime = (
  value?: string,
  options: { locale?: string; todayLabel?: string } = {},
) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16);
  const now = new Date();
  const locale = options.locale || 'zh-CN';
  const time = new Intl.DateTimeFormat(locale, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
  if (
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  ) {
    return `${options.todayLabel || '今日'} ${time}`;
  }
  return new Intl.DateTimeFormat(locale, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
};
