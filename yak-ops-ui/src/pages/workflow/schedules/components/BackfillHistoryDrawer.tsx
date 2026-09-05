import YakButton from '@/components/YakButton';
import {
  cancelWorkflowBackfill,
  listWorkflowBackfills,
  type WorkflowBackfill,
  type WorkflowBackfillStatus,
} from '@/services/workflow/schedules';
import { ReloadOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Drawer, Modal, Select, Table, message } from 'antd';
import { History, ListTree, XCircle } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';

interface BackfillHistoryDrawerProps {
  open: boolean;
  workflowId?: string;
  scheduleId?: string;
  onClose: () => void;
  onOpenTriggers: (backfill: WorkflowBackfill) => void;
}

const STATUS_MESSAGE_IDS: Record<WorkflowBackfillStatus, string> = {
  CREATED: 'pages.workflow.scheduleHistory.status.created',
  RUNNING: 'pages.workflow.scheduleHistory.status.running',
  SUCCEEDED: 'pages.workflow.scheduleHistory.status.succeeded',
  PARTIAL_SUCCESS: 'pages.workflow.scheduleHistory.status.partialSuccess',
  FAILED: 'pages.workflow.scheduleHistory.status.failed',
  CANCELED: 'pages.workflow.scheduleHistory.status.canceled',
};

const OPERATION_MESSAGE_IDS: Record<string, string> = {
  BACKFILL: 'pages.workflow.scheduleHistory.operation.backfill',
  BUSINESS_DATE_RERUN: 'pages.workflow.scheduleHistory.operation.rerun',
};

const formatTime = (value?: string, locale?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale);
};

const BackfillHistoryDrawer = ({
  open,
  workflowId,
  scheduleId,
  onClose,
  onOpenTriggers,
}: BackfillHistoryDrawerProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const [records, setRecords] = useState<WorkflowBackfill[]>([]);
  const [status, setStatus] = useState<WorkflowBackfillStatus>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!open) return;
    setLoading(true);
    try {
      setRecords(await listWorkflowBackfills({ workflowId, scheduleId, status }));
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.scheduleHistory.loadFailed' }),
      );
    } finally {
      setLoading(false);
    }
  }, [open, scheduleId, status, workflowId]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  const cancel = (record: WorkflowBackfill) => {
    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.cancelTitle' }),
      content: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.cancelContent' }),
      okText: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.cancelBatch' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.closed' }),
      okYakButtonProps: { danger: true },
      async onOk() {
        try {
          await cancelWorkflowBackfill(record.id);
          message.success(intlRef.current.formatMessage({ id: 'pages.workflow.scheduleHistory.canceled' }));
          await load();
        } catch (error) {
          message.error(
            error instanceof Error
              ? error.message
              : intlRef.current.formatMessage({ id: 'pages.workflow.scheduleHistory.cancelFailed' }),
          );
        }
      },
    });
  };

  return (
    <Drawer
      open={open}
      width={1240}
      destroyOnClose
      title={
        <div>
          <div className="flex items-center gap-2 text-[14px] font-semibold text-[#344054]">
            <History size={15} />
            {intl.formatMessage({ id: 'pages.workflow.scheduleHistory.title' })}
          </div>
          <div className="mt-0.5 text-[11px] font-normal text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.workflow.scheduleHistory.subtitle' })}
          </div>
        </div>
      }
      onClose={onClose}
      extra={
        <div className="flex items-center gap-2">
          <Select
            allowClear
            placeholder={intl.formatMessage({ id: 'pages.workflow.scheduleHistory.allStatus' })}
            className="w-[130px]"
            value={status}
            onChange={setStatus}
            options={Object.entries(STATUS_MESSAGE_IDS).map(([value, messageId]) => ({
              value,
              label: intl.formatMessage({ id: messageId }),
            }))}
          />
          <YakButton icon={<ReloadOutlined spin={loading} />} onClick={() => void load()} />
        </div>
      }
    >
      <Table
        rowKey="id"
        size="small"
        bordered
        loading={loading}
        dataSource={records}
        scroll={{ x: 1710 }}
        pagination={{
          pageSize: 15,
          showSizeChanger: false,
          showTotal: (total) =>
            intl.formatMessage(
              { id: 'pages.workflow.scheduleHistory.total' },
              { count: total },
            ),
        }}
        columns={[
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.batch' }),
            dataIndex: 'name',
            width: 235,
            fixed: 'left',
            render: (value: string, record: WorkflowBackfill) => (
              <div>
                <div className="font-medium text-[#344054]">{value}</div>
                <div className="mt-1 text-[10px] text-[#98a2b3]">{record.id}</div>
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.typeSource' }),
            width: 215,
            render: (_: unknown, record: WorkflowBackfill) => {
              const operationMessageId = OPERATION_MESSAGE_IDS[record.operationType];
              return (
                <div>
                  <div className="text-[12px] font-medium text-[#475467]">
                    {operationMessageId
                      ? intl.formatMessage({ id: operationMessageId })
                      : record.operationType}
                  </div>
                  <div
                    className="mt-1 max-w-[190px] truncate font-mono text-[10px] text-[#98a2b3]"
                    title={record.sourceExecutionId}
                  >
                    {record.sourceExecutionId
                      ? intl.formatMessage(
                          { id: 'pages.workflow.scheduleHistory.source' },
                          { id: record.sourceExecutionId },
                        )
                      : intl.formatMessage({ id: 'pages.workflow.scheduleHistory.normalBackfill' })}
                  </div>
                </div>
              );
            },
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.status' }),
            dataIndex: 'status',
            width: 100,
            render: (value: WorkflowBackfillStatus) => (
              <span className="text-[12px] font-medium text-[#475467]">
                {intl.formatMessage({ id: STATUS_MESSAGE_IDS[value] })}
              </span>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.businessDate' }),
            width: 190,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div className="text-[11px] text-[#667085]">
                {record.startBusinessDate} ~ {record.endBusinessDate}
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.version' }),
            dataIndex: 'workflowVersionNo',
            width: 125,
            render: (value: number, record: WorkflowBackfill) => (
              <div>
                <div className="text-[12px] text-[#475467]">V{value}</div>
                <div className="mt-1 max-w-[105px] truncate text-[10px] text-[#98a2b3]" title={record.workflowVersionId}>
                  {record.workflowVersionId}
                </div>
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.strategy' }),
            dataIndex: 'executionStrategy',
            width: 115,
            render: (value: string) => <code className="text-[11px] text-[#475467]">{value}</code>,
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.progress' }),
            width: 250,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div className="text-[11px] leading-5 text-[#667085]">
                <div>
                  {intl.formatMessage(
                    { id: 'pages.workflow.scheduleHistory.progressLine1' },
                    {
                      total: record.totalCount,
                      waiting: record.waitingCount,
                      running: record.runningCount,
                    },
                  )}
                </div>
                <div>
                  {intl.formatMessage(
                    { id: 'pages.workflow.scheduleHistory.progressLine2' },
                    {
                      success: record.succeededCount,
                      failed: record.failedCount,
                      skipped: record.skippedCount,
                    },
                  )}
                </div>
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.cronTimezone' }),
            width: 180,
            render: (_: unknown, record: WorkflowBackfill) => (
              <div>
                <code className="text-[11px] text-[#475467]">{record.cronExpression}</code>
                <div className="mt-1 text-[10px] text-[#98a2b3]">{record.timezone}</div>
              </div>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.createdAt' }),
            dataIndex: 'createTime',
            width: 165,
            render: (value: string) => (
              <span className="text-[11px] text-[#98a2b3]">{formatTime(value, intl.locale)}</span>
            ),
          },
          {
            title: intl.formatMessage({ id: 'pages.workflow.scheduleHistory.actions' }),
            width: 170,
            fixed: 'right',
            render: (_: unknown, record: WorkflowBackfill) => (
              <div className="flex items-center gap-1 whitespace-nowrap">
                <YakButton
                  type="text"
                  size="small"
                  icon={<ListTree size={13} />}
                  onClick={() => onOpenTriggers(record)}
                >
                  {intl.formatMessage({ id: 'pages.workflow.scheduleHistory.detail' })}
                </YakButton>
                {record.status === 'RUNNING' ? (
                  <YakButton
                    danger
                    type="text"
                    size="small"
                    icon={<XCircle size={13} />}
                    onClick={() => cancel(record)}
                  >
                    {intl.formatMessage({ id: 'pages.workflow.scheduleHistory.cancel' })}
                  </YakButton>
                ) : null}
              </div>
            ),
          },
        ]}
        className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[12px] [&_.ant-table-thead>tr>th]:!text-[#667085]"
      />
    </Drawer>
  );
};

export default BackfillHistoryDrawer;
