import { useIntl } from '@umijs/max';
import { Tag } from 'antd';
import type { CheckResult, ExecutionStatus } from '../types';

const CHECK_META: Record<CheckResult, { messageId: string; color?: string }> = {
  PASSED: {
    messageId: 'pages.dataQuality.common.status.passed',
    color: 'success',
  },
  NOT_PASSED: {
    messageId: 'pages.dataQuality.common.status.notPassed',
    color: 'error',
  },
  ERROR: {
    messageId: 'pages.dataQuality.common.status.error',
    color: 'error',
  },
  RUNNING: {
    messageId: 'pages.dataQuality.common.status.running',
    color: 'processing',
  },
  NOT_RUN: { messageId: 'pages.dataQuality.common.status.notRun' },
};

const EXECUTION_META: Record<
  ExecutionStatus,
  { messageId: string; color?: string }
> = {
  WAITING: { messageId: 'pages.dataQuality.common.status.waiting' },
  RUNNING: {
    messageId: 'pages.dataQuality.common.status.running',
    color: 'processing',
  },
  SUCCESS: {
    messageId: 'pages.dataQuality.common.status.success',
    color: 'success',
  },
  FAILED: {
    messageId: 'pages.dataQuality.common.status.failed',
    color: 'error',
  },
};

export const CheckResultTag = ({ value }: { value?: CheckResult }) => {
  const intl = useIntl();
  const meta = CHECK_META[value ?? 'NOT_RUN'];
  return (
    <Tag color={meta.color} className="!m-0 !border-0 !text-[11px]">
      {intl.formatMessage({ id: meta.messageId })}
    </Tag>
  );
};

export const ExecutionStatusTag = ({ value }: { value: ExecutionStatus }) => {
  const intl = useIntl();
  const meta = EXECUTION_META[value];
  return (
    <Tag className="!m-0 !border-0 !bg-[#f2f4f7] !text-[11px] !text-[#667085]">
      {intl.formatMessage({ id: meta.messageId })}
    </Tag>
  );
};
