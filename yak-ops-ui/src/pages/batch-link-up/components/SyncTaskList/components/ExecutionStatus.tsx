import { useIntl } from '@umijs/max';
import type { ReactNode } from 'react';

interface ExecutionRecord {
  runMode?: string;
  duration?: string | number;
  readRowCount?: number;
  qps?: number;
  syncSize?: string;
}

interface ExecutionStatusProps {
  record?: ExecutionRecord;
}

interface MetricItemProps {
  label: ReactNode;
  value: ReactNode;
}

const MetricItem = ({ label, value }: MetricItemProps) => (
  <div className="min-w-0">
    <div className="mb-0.5 whitespace-nowrap text-[11px] leading-4 text-[#98a2b3]">
      {label}
    </div>

    <div className="truncate whitespace-nowrap text-[13px] font-medium leading-5 text-[#344054] tabular-nums">
      {value}
    </div>
  </div>
);

const ExecutionStatus = ({ record }: ExecutionStatusProps) => {
  const intl = useIntl();
  const isManual = record?.runMode === 'MANUAL';
  const durationUnit = intl.formatMessage({
    id: 'pages.batchLinkUp.execution.seconds',
  });
  const rowsUnit = intl.formatMessage({
    id: 'pages.batchLinkUp.execution.rows',
  });
  const rowsPerSecondUnit = intl.formatMessage({
    id: 'pages.batchLinkUp.execution.rowsPerSecond',
  });

  return (
    <div className="min-w-[190px]">
      <div className="mb-2 flex items-center gap-2">
        <span className="inline-flex items-center gap-1 text-[13px] font-medium text-[#344054]">
          <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[11px] font-medium leading-5 text-[#667085]">
            {intl.formatMessage({
              id: isManual
                ? 'pages.batchLinkUp.execution.manual'
                : 'pages.batchLinkUp.execution.scheduled',
            })}
          </span>
        </span>
      </div>

      <div className="grid grid-cols-2 gap-x-5 gap-y-2">
        <MetricItem
          label={intl.formatMessage({ id: 'pages.batchLinkUp.execution.time' })}
          value={
            record?.duration !== undefined && record?.duration !== null
              ? `${record.duration} ${durationUnit}`
              : '-'
          }
        />

        <MetricItem
          label={intl.formatMessage({ id: 'pages.batchLinkUp.execution.amount' })}
          value={`${record?.readRowCount ?? 0} ${rowsUnit}`}
        />

        <MetricItem
          label={intl.formatMessage({ id: 'pages.batchLinkUp.execution.qps' })}
          value={`${record?.qps ?? 0} ${rowsPerSecondUnit}`}
        />

        <MetricItem
          label={intl.formatMessage({ id: 'pages.batchLinkUp.execution.size' })}
          value={record?.syncSize || '-'}
        />
      </div>
    </div>
  );
};

export default ExecutionStatus;
