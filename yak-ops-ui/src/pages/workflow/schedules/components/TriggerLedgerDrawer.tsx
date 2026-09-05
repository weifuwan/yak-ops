import YakButton from '@/components/YakButton';
import {
  listWorkflowScheduleTriggers,
  type WorkflowSchedule,
  type WorkflowScheduleTrigger,
  type WorkflowScheduleTriggerStatus,
} from '@/services/workflow/schedules';
import { ReloadOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Drawer, Select, Table, message } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

interface TriggerLedgerDrawerProps {
  open: boolean;
  schedule?: WorkflowSchedule;
  backfillId?: string;
  backfillName?: string;
  onClose: () => void;
}

const STATUS_MESSAGE_IDS: Record<WorkflowScheduleTriggerStatus, string> = {
  RECEIVED: 'pages.workflow.trigger.status.received',
  WAITING: 'pages.workflow.trigger.status.waiting',
  LAUNCHING: 'pages.workflow.trigger.status.launching',
  REACTIVATING: 'pages.workflow.trigger.status.reactivating',
  RUNNING: 'pages.workflow.trigger.status.running',
  SUCCEEDED: 'pages.workflow.trigger.status.succeeded',
  FAILED: 'pages.workflow.trigger.status.failed',
  CANCELED: 'pages.workflow.trigger.status.canceled',
  SKIPPED: 'pages.workflow.trigger.status.skipped',
};

const SOURCE_MESSAGE_IDS: Record<string, string> = {
  MANUAL: 'pages.workflow.trigger.source.manual',
  MISFIRE_RECOVERY: 'pages.workflow.trigger.source.misfire',
  BUSINESS_DATE_RERUN: 'pages.workflow.trigger.source.rerun',
};

const formatTime = (value?: string, locale?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale);
};

const TriggerLedgerDrawer = ({
  open,
  schedule,
  backfillId,
  backfillName,
  onClose,
}: TriggerLedgerDrawerProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const [records, setRecords] = useState<WorkflowScheduleTrigger[]>([]);
  const [status, setStatus] = useState<WorkflowScheduleTriggerStatus>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!open || (!schedule?.id && !backfillId)) return;
    setLoading(true);
    try {
      setRecords(
        await listWorkflowScheduleTriggers({
          scheduleId: schedule?.id,
          backfillId,
          status,
          limit: backfillId ? 1000 : 200,
        }),
      );
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.trigger.loadFailed' }),
      );
    } finally {
      setLoading(false);
    }
  }, [backfillId, open, schedule?.id, status]);

  useEffect(() => {
    if (open) setStatus(undefined);
  }, [backfillId, open, schedule?.id]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  return (
    <Drawer
      open={open}
      width={1040}
      title={
        <div>
          <div className="text-[14px] font-semibold text-[#344054]">
            {intl.formatMessage({
              id: backfillId
                ? 'pages.workflow.trigger.batchTitle'
                : 'pages.workflow.trigger.title',
            })}
          </div>
          <div className="mt-0.5 text-[11px] font-normal text-[#98a2b3]">
            {backfillName || schedule?.name || '-'} ·{' '}
            {intl.formatMessage({
              id: backfillId
                ? 'pages.workflow.trigger.batchIsolation'
                : 'pages.workflow.trigger.normalIsolation',
            })}
          </div>
        </div>
      }
      onClose={onClose}
      destroyOnClose
      extra={
        <div className="flex items-center gap-2">
          <Select
            allowClear
            placeholder={intl.formatMessage({ id: 'pages.workflow.trigger.allStatus' })}
            className="w-[120px]"
            value={status}
            onChange={(value) => setStatus(value)}
            options={Object.entries(STATUS_MESSAGE_IDS).map(([value, messageId]) => ({
              value,
              label: intl.formatMessage({ id: messageId }),
            }))}
          />
          <YakButton icon={<ReloadOutlined spin={loading} />} onClick={() => void load()} />
        </div>
      }
    >
      <div className="mb-3 rounded-sm bg-[#f8f9fb] px-3 py-2 text-[11px] leading-5 text-[#667085]">
        {intl.formatMessage({
          id: backfillId
            ? 'pages.workflow.trigger.batchHint'
            : 'pages.workflow.trigger.normalHint',
        })}
      </div>
      <Table
        rowKey="id"
        size="small"
        bordered
        loading={loading}
        dataSource={records}
        scroll={{ x: 1430 }}
        pagination={{
          pageSize: 20,
          showSizeChanger: false,
          showTotal: (total) =>
            intl.formatMessage({ id: 'pages.workflow.trigger.total' }, { count: total }),
        }}
        columns={[
          {
            title: intl.formatMessage({ id: 'pages.workflow.trigger.status' }),
            dataIndex: 'status',
            width: 90,
            fixed: 'left',
            render: (value: WorkflowScheduleTriggerStatus) => (
              <span className="text-[12px] font-medium text-[#475467]">
                {intl.formatMessage({ id: STATUS_MESSAGE_IDS[value] })}
              </span>
            ),
          },
          {
            title: 'businessDate',
            dataIndex: 'businessDate',
            width: 115,
            render: (value?: string) => (
              <code className="text-[11px] text-[#475467]">{value || '-'}</code>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.trigger.planActual' }),
            width: 205,
            render: (_: unknown, record: WorkflowScheduleTrigger) => (
              <div className="text-[11px] leading-5 text-[#667085]">
                <div>
                  {intl.formatMessage(
                    { id: 'pages.workflow.trigger.planned' },
                    { time: formatTime(record.plannedFireTime, intl.locale) },
                  )}
                </div>
                <div>
                  {intl.formatMessage(
                    { id: 'pages.workflow.trigger.actual' },
                    { time: formatTime(record.actualFireTime, intl.locale) },
                  )}
                </div>
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.trigger.source' }),
            dataIndex: 'triggerSource',
            width: 135,
            render: (value: string) => {
              const messageId = SOURCE_MESSAGE_IDS[value];
              return (
                <span className="text-[12px] text-[#667085]">
                  {messageId ? intl.formatMessage({ id: messageId }) : value}
                </span>
              );
            },
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.trigger.strategy' }),
            dataIndex: 'executionStrategy',
            width: 120,
            render: (value: string) => <code className="text-[11px] text-[#475467]">{value}</code>,
          },
          {
            title: 'WorkflowExecution',
            dataIndex: 'workflowExecutionId',
            width: 230,
            render: (value?: string, record?: WorkflowScheduleTrigger) =>
              value ? (
                <div>
                  <code className="text-[11px] text-[#475467]">{value}</code>
                  <div className="mt-1 text-[11px] text-[#98a2b3]">
                    {record?.executionStatus || '-'}
                  </div>
                </div>
              ) : (
                <span className="text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.workflow.trigger.notCreated' })}
                </span>
              ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.trigger.message' }),
            dataIndex: 'message',
            width: 280,
            render: (value?: string, record?: WorkflowScheduleTrigger) => (
              <div className="text-[11px] leading-5 text-[#667085]">
                <div>{value || '-'}</div>
                {record?.errorMessage ? (
                  <div className="mt-1 text-[#b42318]">{record.errorMessage}</div>
                ) : null}
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.trigger.completedAt' }),
            dataIndex: 'completedAt',
            width: 165,
            render: (value?: string) => (
              <span className="text-[11px] text-[#98a2b3]">{formatTime(value, intl.locale)}</span>
            ),
          },
        ]}
        className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085]"
      />
    </Drawer>
  );
};

export default TriggerLedgerDrawer;
